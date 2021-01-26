package sushi.hardcore.aira.utils

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
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

    fun openFileForDownload(context: Context, fileName: String): OutputStream? {
        val fileExtension = fileName.substringAfterLast(".")
        val dateExtension = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val datedFilename = if (fileName.contains(".")) {
            val basename = fileName.substringBeforeLast(".")
            """${basename}_$dateExtension.$fileExtension"""
        } else {
            fileName + "_" + dateExtension
        }
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), datedFilename).outputStream()
        }
    }
}