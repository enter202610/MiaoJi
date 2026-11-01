package com.inkledger.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class InkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AUTO,
                getString(R.string.channel_auto_record),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_auto_record_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONFIRM,
                "自动记账·需确认",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "识别到支付后弹出确认，可一键忽略" }
        )
    }

    companion object {
        const val CHANNEL_AUTO = "auto_record"
        const val CHANNEL_CONFIRM = "auto_confirm"
    }
}
