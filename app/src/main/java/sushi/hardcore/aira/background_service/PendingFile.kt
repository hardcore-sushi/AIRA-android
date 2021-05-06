package sushi.hardcore.aira.background_service

open class PendingFile(val fileName: String, val fileSize: Long) {
    var transferred = 0
}