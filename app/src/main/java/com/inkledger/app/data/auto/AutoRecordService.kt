package com.inkledger.app.data.auto

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.inkledger.app.InkApp
import com.inkledger.app.MainActivity
import com.inkledger.app.R
import com.inkledger.app.data.db.AppDatabase
import com.inkledger.app.data.db.TransactionEntity
import com.inkledger.app.data.prefs.SettingsRepository
import com.inkledger.app.util.formatMoney
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 钱迹式自动记账（公开 API 版）：监听微信 / 支付宝的支付通知，自动解析金额入账。
 * 只读通知文本，不拦截任何触摸事件；未授权时完全不工作，不影响 App 其他功能。
 */
class AutoRecordService : NotificationListenerService() {

    companion object {
        private val PACKAGES = setOf("com.tencent.mm", "com.eg.android.AlipayGphone")
        private val TRIGGERS =
            listOf("支付", "支出", "消费", "付款", "收款", "到账", "退款", "入账", "红包", "成功")
        private val INCOME_HINTS =
            listOf("收款", "已收", "到账", "入账", "退款", "红包", "你已收款", "余额宝", "零钱入账")
        private val amountWithSymbol = Regex("""[¥￥]\s*([0-9,]+(?:\.\d{1,2})?)""")
        private val amountWithYuan = Regex("""([0-9,]+(?:\.\d{1,2})?)\s*元""")
        private val recentKeys = ArrayDeque<String>()

        /** 通知使用权是否已授予本应用 */
        fun isListenerEnabled(context: Context): Boolean =
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName !in PACKAGES) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        val full = "$title $text".trim()
        if (full.isBlank()) return
        if (!TRIGGERS.any { full.contains(it) }) return

        val amountStr = amountWithSymbol.find(full)?.groupValues?.get(1)
            ?: amountWithYuan.find(full)?.groupValues?.get(1)
            ?: return
        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return
        val amountMinor = Math.round(amount * 100)
        if (amountMinor <= 0 || amountMinor > 99_999_999_999L) return

        val isIncome = INCOME_HINTS.any { full.contains(it) }

        // 去重：同来源同金额同方向，短时间内只记一次
        val key = "${sbn.packageName}|$amountMinor|$isIncome"
        synchronized(recentKeys) {
            if (recentKeys.contains(key)) return
            recentKeys.add(key)
            while (recentKeys.size > 300) recentKeys.removeFirst()
        }

        val app = applicationContext
        val db = AppDatabase.get(app)
        val prefs = SettingsRepository(app)
        runBlocking {
            val settings = prefs.flow.first()
            if (!settings.autoRecordEnabled) return@runBlocking
            val dup = db.txDao().countAutoSimilar(
                amountMinor,
                if (isIncome) 1 else 0,
                System.currentTimeMillis() - 3 * 60_000L
            )
            if (dup > 0) return@runBlocking
            val now = System.currentTimeMillis()
            val txId = db.txDao().insert(
                TransactionEntity(
                    amountMinor = amountMinor,
                    type = if (isIncome) 1 else 0,
                    categoryId = if (isIncome) 104L else 13L,
                    note = if (sbn.packageName == "com.tencent.mm") "微信支付" else "支付宝",
                    timestamp = now,
                    createdAt = now,
                    source = 4
                )
            )
            postResult(app, txId, amountMinor, isIncome, settings.autoConfirmMode)
        }
    }

    private fun postResult(
        context: Context,
        txId: Long,
        amountMinor: Long,
        isIncome: Boolean,
        confirmMode: Boolean
    ) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val ignore = PendingIntent.getBroadcast(
            context, txId.toInt(),
            Intent(context, AutoConfirmReceiver::class.java)
                .setAction(AutoConfirmReceiver.ACTION_IGNORE)
                .putExtra(AutoConfirmReceiver.EXTRA_ID, txId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val dir = if (isIncome) "收入" else "支出"
        val n = NotificationCompat.Builder(
            context,
            if (confirmMode) InkApp.CHANNEL_CONFIRM else InkApp.CHANNEL_AUTO
        )
            .setSmallIcon(R.drawable.ic_stat_ink)
            .setContentTitle("自动记账 · $dir ${formatMoney(amountMinor)}")
            .setContentText("识别到支付通知，已自动入账；若识别有误可忽略")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "忽略此笔", ignore)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(txId.toInt(), n)
        } catch (_: SecurityException) {
            // 用户未授予通知权限，静默入账即可
        }
    }
}
