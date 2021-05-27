package sushi.hardcore.aira.background_service

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import sushi.hardcore.aira.R
import sushi.hardcore.aira.databinding.DialogAskFileBinding
import sushi.hardcore.aira.utils.FileUtils

class FilesReceiver(
        val files: List<ReceiveFile>,
        private val onAccepted: (FilesReceiver) -> Unit,
        private val onAborted: (FilesReceiver) -> Unit,
        context: Context,
        notificationManager: NotificationManagerCompat,
        sessionName: String
): FilesTransfer(context, notificationManager, sessionName) {
    var shouldAsk = true

    @SuppressLint("SetTextI18n")
    fun ask(activity: AppCompatActivity, senderName: String) {
        val dialogBinding = DialogAskFileBinding.inflate(activity.layoutInflater)
        dialogBinding.textTitle.text = activity.getString(R.string.want_to_send_files, senderName)+':'
        val filesInfo = StringBuilder()
        for (file in files) {
            filesInfo.appendLine(file.fileName+" ("+FileUtils.formatSize(file.fileSize)+')')
        }
        dialogBinding.textFilesInfo.text = filesInfo.substring(0, filesInfo.length-1)
        AlertDialog.Builder(activity, R.style.CustomAlertDialog)
                .setTitle(R.string.download_file_request)
                .setView(dialogBinding.root)
                .setCancelable(false)
                .setPositiveButton(R.string.download) { _, _ ->
                    onAccepted(this)
                }
                .setNegativeButton(R.string.refuse) { _, _ ->
                    onAborted(this)
                }
                .setOnDismissListener {
                    shouldAsk = false
                }
                .show()
    }
}