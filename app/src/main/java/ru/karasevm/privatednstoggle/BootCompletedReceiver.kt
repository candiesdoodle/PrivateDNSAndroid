package ru.karasevm.privatednstoggle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.karasevm.privatednstoggle.service.WifiMonitorService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val serviceIntent = Intent(context, WifiMonitorService::class.java)
            context.startService(serviceIntent)
        }
    }
}