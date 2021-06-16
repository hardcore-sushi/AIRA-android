package sushi.hardcore.aira.utils

import java.net.InetAddress

object StringUtils {
    fun beautifyFingerprint(fingerprint: String): String {
        val newFingerprint = StringBuilder(fingerprint.length+7)
        for (i in 0..fingerprint.length-8 step 4) {
            newFingerprint.append(fingerprint.slice(i until i+4)+" ")
        }
        newFingerprint.append(fingerprint.slice(fingerprint.length-4 until fingerprint.length))
        return newFingerprint.toString()
    }

    fun getIpFromInetAddress(addr: InetAddress): String {
        val rawIp = addr.hostAddress
        val i = rawIp.lastIndexOf('%')
        return if (i == -1) {
            rawIp
        } else {
            rawIp.substring(0, i)
        }
    }

    fun sanitizeName(name: String): String {
        return name.replace('\n', ' ')
    }

    fun toTwoDigits(number: Int): String {
        return if (number < 10) {
            "0$number"
        } else {
            number.toString()
        }
    }
}