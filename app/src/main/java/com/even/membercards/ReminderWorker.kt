package com.even.membercards

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CardRepository
import com.even.membercards.data.EntryType
import com.even.membercards.data.InvoiceRepository
import com.even.membercards.data.InvoiceRules
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.Shortcuts
import com.even.membercards.data.formatMoney
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * 每天早上檢查一次：
 * - 會員卡／票券 7 天內、明天、今天到期
 * - 定期收支明天扣款／入帳
 * - 本月支出達預算 80%、超過預算
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        runCatching { check(applicationContext, fetch = true) }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "reminders"
        private const val WORK = "daily_reminders"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            // 下次早上 9:00 開始，每 24 小時一次
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(9, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next)
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofHours(24))
                .setInitialDelay(delay)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        /** 立即檢查一次（也給「測試提醒」用） */
        fun check(context: Context, fetch: Boolean = false): Int {
            val settings = AppSettings.instance(context)
            if (!settings.reminders || !canNotify(context)) return 0
            ensureChannel(context)
            var sent = 0
            val today = LocalDate.now()

            // 1. 卡片／票券到期
            CardRepository.instance(context).cards.toList().forEach { c ->
                if (c.expiry <= 0 || c.used) return@forEach
                val exp = Instant.ofEpochMilli(c.expiry).atZone(ZoneOffset.UTC).toLocalDate()
                val days = ChronoUnit.DAYS.between(today, exp)
                val stage = when {
                    days == 0L -> "0"
                    days == 1L -> "1"
                    days in 2..7 -> "7"
                    else -> null
                } ?: return@forEach
                val key = "exp:${c.id}:$exp:$stage"
                if (settings.reminderSent(key)) return@forEach
                val what = if (c.isCoupon) "票券" else "會員卡"
                val whenText = when (stage) {
                    "0" -> "今天到期"
                    "1" -> "明天到期"
                    else -> "$days 天後到期（$exp）"
                }
                notify(
                    context,
                    key.hashCode(),
                    "$what「${c.name}」$whenText",
                    if (c.isCoupon) "記得在到期前使用" else "記得確認是否需要續卡或使用點數",
                    Shortcuts.openIntent(context, c.id),
                )
                settings.markReminderSent(key)
                sent++
            }

            // 2. 定期收支明天到期
            val ledger = LedgerRepository.instance(context)
            val tomorrow = today.plusDays(1)
            ledger.recurring.toList().forEach { r ->
                if (!r.enabled) return@forEach
                val due = ledger.nextDue(r)
                if (due != tomorrow) return@forEach
                val key = "rec:${r.id}:$due"
                if (settings.reminderSent(key)) return@forEach
                val verb = if (r.type == EntryType.INCOME) "入帳" else "扣款"
                notify(
                    context,
                    key.hashCode(),
                    "明天$verb：${r.title.ifBlank { r.category }} ${formatMoney(r.amount)}",
                    "定期收支會在明天自動記帳",
                    openApp(context),
                )
                settings.markReminderSent(key)
                sent++
            }

            // 3. 預算
            val budget = settings.monthlyBudget
            if (budget > 0) {
                val ym = YearMonth.now()
                val spent = ledger.monthExpense(ym)
                val level = when {
                    spent > budget -> "100"
                    spent >= budget * 0.8 -> "80"
                    else -> null
                }
                if (level != null) {
                    val key = "budget:$ym:$level"
                    if (!settings.reminderSent(key)) {
                        notify(
                            context,
                            key.hashCode(),
                            if (level == "100") "本月已超出預算 ${formatMoney(spent - budget)}" else "本月支出已達預算 80%",
                            "已花 ${formatMoney(spent)} / 預算 ${formatMoney(budget.toDouble())}",
                            openApp(context),
                        )
                        settings.markReminderSent(key)
                        sent++
                    }
                }
            }
            // 4. 發票對獎：開獎後自動下載中獎號碼並通知中獎
            val inv = InvoiceRepository.instance(context)
            if (fetch && inv.invoices.isNotEmpty()) {
                val latest = InvoiceRules.latestDrawnPeriod(today)
                if (inv.draw(latest) == null) {
                    runCatching { runBlocking { inv.fetchDraws() } }
                }
            }
            inv.invoices.toList().filter { (it.prize ?: 0) > 0 }.forEach { w ->
                val key = "inv:${w.number}:${w.prize}"
                if (settings.reminderSent(key)) return@forEach
                notify(
                    context,
                    key.hashCode(),
                    "🎉 發票中獎！${w.prizeName} ${formatMoney(w.prize!!.toDouble())}",
                    "${w.number}（${InvoiceRules.label(w.period)}），兌獎期限 ${InvoiceRules.claimDeadline(w.period) ?: ""}",
                    openApp(context),
                )
                settings.markReminderSent(key)
                sent++
            }
            return sent
        }

        private fun openApp(context: Context): Intent =
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                            description = "會員卡／票券到期、定期扣款、預算提醒"
                        }
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun notify(context: Context, id: Int, title: String, text: String, intent: Intent) {
            if (!canNotify(context)) return
            val pi = PendingIntent.getActivity(
                context,
                id,
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_barcode)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }
}
