package com.even.membercards

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.view.View
import android.widget.RemoteViews
import com.even.membercards.data.AppSettings
import com.even.membercards.data.Barcodes
import com.even.membercards.data.CardRepository
import com.even.membercards.ui.effectiveCarrier
import com.even.membercards.ui.formatPhone

/** 桌面小工具：手機條碼載具 + 主要電話。點條碼／電話開全螢幕。 */
class CarrierWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        const val ACTION_SHOW_CARRIER = "com.even.membercards.SHOW_CARRIER"
        const val ACTION_SHOW_PHONE = "com.even.membercards.SHOW_PHONE"

        fun openIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java)
                    .setAction(action)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** 設定或卡片變更後呼叫，更新所有小工具。 */
        fun updateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, CarrierWidget::class.java))
                ids.forEach { update(context, manager, it) }
            }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val settings = AppSettings.instance(context)
            val repo = CardRepository.instance(context)
            val carrier = effectiveCarrier(settings, repo)
            val phone = settings.phones.firstOrNull()?.number.orEmpty()

            val views = RemoteViews(context.packageName, R.layout.widget_carrier)
            val bmp = if (carrier.isBlank()) null else runCatching { Barcodes.encode(carrier, "CODE_39", 700) }.getOrNull()
            if (bmp != null) {
                views.setImageViewBitmap(R.id.widget_barcode, bmp)
                views.setViewVisibility(R.id.widget_barcode, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_barcode, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            }
            views.setTextViewText(R.id.widget_carrier, carrier)
            views.setTextViewText(R.id.widget_phone, if (phone.isBlank()) "" else "☎ " + formatPhone(phone))

            views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, ACTION_SHOW_CARRIER, 1))
            views.setOnClickPendingIntent(R.id.widget_barcode_box, openIntent(context, ACTION_SHOW_CARRIER, 1))
            views.setOnClickPendingIntent(R.id.widget_phone, openIntent(context, ACTION_SHOW_PHONE, 2))
            manager.updateAppWidget(id, views)
        }
    }
}

/** 快速設定磚：下拉通知列一點就開載具條碼。 */
class CarrierTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(CarrierWidget.ACTION_SHOW_CARRIER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
