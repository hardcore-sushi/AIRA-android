package sushi.hardcore.aira.background_service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import sushi.hardcore.aira.AIRADatabase
import sushi.hardcore.aira.Constants

class SystemBroadcastReceiver: BroadcastReceiver() {
    init {
        AIRADatabase.init()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("startAtBoot", true) && !AIRAService.isServiceRunning) {
                val databaseFolder = Constants.getDatabaseFolder(context)
                val isProtected = AIRADatabase.isIdentityProtected(databaseFolder)
                val name = AIRADatabase.getIdentityName(databaseFolder)
                if (name != null && !isProtected) {
                    if (AIRADatabase.loadIdentity(databaseFolder, null)) {
                        AIRADatabase.clearCache()
                        val serviceIntent = Intent(context, AIRAService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                }
            }
        }
    }
}