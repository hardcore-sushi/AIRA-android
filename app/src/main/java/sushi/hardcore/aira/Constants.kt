package sushi.hardcore.aira

import android.content.Context
import java.io.File

object Constants {
    const val port = 7530
    const val mDNSServiceName = "AIRA Node"
    const val mDNSServiceType = "_aira._tcp"
    const val fileSizeLimit = 16380000
    const val MSG_LOADING_COUNT = 20
    const val FILE_CHUNK_SIZE = 1023996
    const val MAX_AVATAR_SIZE = 10000000
    private const val databaseName = "AIRA.db"

    fun getDatabaseFolder(context: Context): String {
        return getDatabasePath(context).parent!!
    }

    fun getDatabasePath(context: Context): File {
        return context.getDatabasePath(databaseName)
    }
}