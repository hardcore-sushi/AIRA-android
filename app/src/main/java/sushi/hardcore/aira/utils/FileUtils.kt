package sushi.hardcore.aira.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import sushi.hardcore.aira.background_service.SendFile
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.log10

object FileUtils {
    private val units = arrayOf("B", "kB", "MB", "GB", "TB")
    fun formatSize(size: Long): String {
        if (size <= 0) {
            return "0 B"
        }
        val digitGroups = (log10(size.toDouble()) / log10(1000.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / 1000.0.pow(digitGroups.toDouble())
        ) + " " + units[digitGroups]
    }

    fun openFileFromUri(context: Context, uri: Uri): SendFile? {
        var sendFile: SendFile? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    context.contentResolver.openInputStream(uri)?.let { inputStream ->
                        val fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                        val fileSize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                        sendFile = SendFile(fileName, fileSize, inputStream)
                    }
                } catch (e: FileNotFoundException) {
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
            }
            cursor.close()
        }
        return sendFile
    }

    class DownloadFile(val fileName: String, val outputStream: OutputStream?)

    fun openFileForDownload(context: Context, fileName: String): DownloadFile {
        val fileExtension = fileName.substringAfterLast(".")
        val dateExtension = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val datedFilename = if (fileName.contains(".")) {
            val basename = fileName.substringBeforeLast(".")
            """${basename}_$dateExtension.$fileExtension"""
        } else {
            fileName + "_" + dateExtension
        }
        val outputStream = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.TITLE, datedFilename)
                        put(MediaStore.Images.Media.DISPLAY_NAME, datedFilename)
                        put(MediaStore.Images.Media.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension))
                    }
            )?.let {
                context.contentResolver.openOutputStream(it)
            }
        } else {
            @Suppress("Deprecation")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), File(datedFilename).name).outputStream()
        }
        return DownloadFile(datedFilename, outputStream)
    }
}