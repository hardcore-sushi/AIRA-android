package sushi.hardcore.aira.background_service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

class NotificationBroadcastReceiver: BroadcastReceiver() {
    companion object {
        const val ACTION_MARK_READ = "mark_read"
        const val ACTION_CANCEL_FILE_TRANSFER = "cancel"
        const val ACTION_REPLY = "reply"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    override fun onReceive(context: Context?, intent: Intent) {
        intent.getBundleExtra("bundle")?.let { bundle ->
            (bundle.getBinder("binder") as AIRAService.AIRABinder?)?.let { binder ->
                val sessionId = bundle.getInt("sessionId")
                val airaService = binder.getService()
                when (intent.action) {
                    ACTION_MARK_READ -> airaService.setSeen(sessionId, true)
                    ACTION_CANCEL_FILE_TRANSFER -> airaService.cancelFileTransfer(sessionId)
                    ACTION_REPLY -> RemoteInput.getResultsFromIntent(intent)?.getString(KEY_TEXT_REPLY)?.let { reply ->
                        airaService.sendTo(sessionId, Protocol.newMessage(reply))
                        airaService.setSeen(sessionId, true)
                    }
                    else -> {}
                }
            }
        }
    }
}