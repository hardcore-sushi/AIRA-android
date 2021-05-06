package sushi.hardcore.aira

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

    override fun onStop() {
        super.onStop()
        if (::airaService.isInitialized) {
            airaService.isAppInBackground = true
            airaService.uiCallbacks = null
            unbindService(serviceConnection)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::serviceIntent.isInitialized) {
            serviceIntent = Intent(this, AIRAService::class.java)
        }
    }
}