package sushi.hardcore.aira.background_service

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.aira.R
import sushi.hardcore.aira.utils.FileUtils
import java.io.OutputStream

class ReceiveFileTransfer(
        fileName: String,
        fileSize: Long,
        private val onAccepted: (fileTransfer: ReceiveFileTransfer) -> Unit,
        private val onAborted: () -> Unit,
): FileTransfer(fileName, fileSize) {
    var shouldAsk = true
    var outputStream: OutputStream? = null

    @SuppressLint("SetTextI18n")
    fun ask(activity: AppCompatActivity, senderName: String) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_ask_file, null)
        dialogView.findViewById<TextView>(R.id.text_title).text = activity.getString(R.string.want_to_send_a_file, senderName, ":")
        dialogView.findViewById<TextView>(R.id.text_file_info).text = fileName+" ("+FileUtils.formatSize(fileSize)+")"
        AlertDialog.Builder(activity)
                .setTitle(R.string.download_file_request)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton(R.string.download) { _, _ ->
                    outputStream = FileUtils.openFileForDownload(activity, fileName)
                    if (outputStream == null) {
                        onAborted()
                    } else {
                        onAccepted(this)
                    }
                    shouldAsk = false
                }
                .setNegativeButton(R.string.refuse) { _, _ ->
                    onAborted()
                    shouldAsk = false
                }
                .show()
    }
}