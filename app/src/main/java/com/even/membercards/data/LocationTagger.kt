package com.even.membercards.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 消費時記錄位置：只用系統 LocationManager（不需要 Google Play 服務），
 * 地址用系統 Geocoder 反查（需要網路，查不到就只存座標）。
 */
object LocationTagger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun hasPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Android 10 以上，自動記帳在背景發生，需要「一律允許」 */
    fun hasBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(context: Context, p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** 背景取得位置後寫回該筆紀錄 */
    fun tag(context: Context, entryId: String) {
        val app = context.applicationContext
        if (!hasPermission(app)) return
        scope.launch {
            val loc = current(app) ?: return@launch
            val ledger = LedgerRepository.instance(app)
            // 先存座標，地址查到再補上
            ledger.setLocation(entryId, loc.latitude, loc.longitude, loc.accuracy)
            val place = address(app, loc.latitude, loc.longitude)
            if (place.isNotBlank()) ledger.setLocation(entryId, loc.latitude, loc.longitude, loc.accuracy, place)
        }
    }

    /** 取得目前位置：1 分鐘內的快取直接用，否則重新定位（最多約 25 秒） */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val last = enabled.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && now - last.time < 60_000 && last.accuracy <= 150f) return last

        val order = buildList {
            if (Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in enabled) add(LocationManager.FUSED_PROVIDER)
            if (LocationManager.NETWORK_PROVIDER in enabled) add(LocationManager.NETWORK_PROVIDER)
            if (LocationManager.GPS_PROVIDER in enabled) add(LocationManager.GPS_PROVIDER)
        }
        for (p in order) {
            val fix = withTimeoutOrNull(if (p == LocationManager.GPS_PROVIDER) 20_000 else 10_000) { once(lm, p) }
            if (fix != null) return fix
        }
        // 實在定不到位，用 30 分鐘內的舊位置
        return last?.takeIf { now - it.time < 30 * 60_000 }
    }

    @SuppressLint("MissingPermission")
    private suspend fun once(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine<Location?> { cont ->
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, { it.run() }) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    /** 座標反查地址，例如「台北市中正區重慶南路一段122號」 */
    suspend fun address(context: Context, lat: Double, lng: Double): String {
        if (!Geocoder.isPresent()) return ""
        val geocoder = Geocoder(context, Locale.TAIWAN)
        val a: Address? = if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Address?> { cont ->
                    try {
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return a?.let { format(it) } ?: ""
    }

    private fun format(a: Address): String {
        val line = a.getAddressLine(0).orEmpty()
            .replace(Regex("^\\d{3,6}"), "")
            .removePrefix("台灣").removePrefix("臺灣").removePrefix("Taiwan, ")
            .trim()
        return line.ifBlank {
            listOfNotNull(a.adminArea, a.locality ?: a.subAdminArea, a.thoroughfare, a.subThoroughfare).joinToString("")
        }
    }

    /** 開地圖用的 geo: 網址 */
    fun mapUri(lat: Double, lng: Double, label: String): String {
        val q = if (label.isBlank()) "$lat,$lng" else "$lat,$lng(${label.replace("(", " ").replace(")", " ")})"
        return "geo:$lat,$lng?q=" + android.net.Uri.encode(q)
    }
}
