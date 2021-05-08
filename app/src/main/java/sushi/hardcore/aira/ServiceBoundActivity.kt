package sushi.hardcore.aira

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.aira.background_service.AIRAService

open class ServiceBoundActivity: AppCompatActivity() {
    protected lateinit var airaService: AIRAService
    protected lateinit var serviceConnection: ServiceConnection
    protected lateinit var serviceIntent: Intent

    protected fun isServiceInitialized(): Boolean {
        return ::airaService.isInitialized
    }

    override fun onPause() {
        super.onPause()
        if (::airaService.isInitialized) {
            airaService.isAppInBackground = true
            airaService.uiCallbacks = null
            unbindService(serviceConnection)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::serviceIntent.isInitialized) {
            serviceIntent = Intent(this, AIRAService::class.java)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}