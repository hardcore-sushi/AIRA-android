package sushi.hardcore.aira.utils

import sushi.hardcore.aira.ChatItem

object TimeUtils {
    fun getTimestamp(): Long {
        return System.currentTimeMillis()/1000
    }
    fun isInTheSameDay(first: ChatItem, second: ChatItem): Boolean {
        return first.year == second.year && first.dayOfYear == second.dayOfYear
    }
}