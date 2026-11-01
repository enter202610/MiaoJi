package com.inkledger.app.data.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.inkledger.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking

/** 「忽略此笔」：删除自动记账误识别的交易并撤掉通知 */
class AutoConfirmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_IGNORE) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0) return
        val pending = goAsync()
        Thread {
            try {
                runBlocking {
                    AppDatabase.get(context).txDao().getById(id)?.let {
                        AppDatabase.get(context).txDao().delete(it)
                    }
                }
                NotificationManagerCompat.from(context).cancel(id.toInt())
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_IGNORE = "com.inkledger.app.ACTION_IGNORE_TX"
        const val EXTRA_ID = "id"
    }
}
