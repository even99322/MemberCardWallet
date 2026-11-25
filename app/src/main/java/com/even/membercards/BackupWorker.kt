package com.even.membercards

import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.even.membercards.data.AppSettings
import com.even.membercards.data.Backup
import java.time.Duration
import java.time.LocalDateTime

/** 每天凌晨自動把完整備份覆寫到使用者選的雲端位置（Google 雲端硬碟）。 */
class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val settings = AppSettings.instance(applicationContext)
        if (!settings.autoBackup || settings.backupUri.isBlank()) return Result.success()
        val r = Backup.backupNow(applicationContext)
        if (r.isFailure) {
            // 連續失敗才通知：WorkManager 會依退避策略重試
            if (runAttemptCount >= 2) {
                ReminderWorker.ensureChannel(applicationContext)
                ReminderWorker.notify(
                    applicationContext,
                    7301,
                    "自動備份失敗",
                    "無法寫入雲端備份檔，請到 設定 → 資料 → 雲端備份 重新選擇備份位置。",
                    Intent(applicationContext, MainActivity::class.java),
                )
                return Result.success()
            }
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "auto_backup"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            // 每天凌晨 3 點左右
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(3, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val req = PeriodicWorkRequestBuilder<BackupWorker>(Duration.ofHours(24))
                .setInitialDelay(Duration.between(now, next))
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
