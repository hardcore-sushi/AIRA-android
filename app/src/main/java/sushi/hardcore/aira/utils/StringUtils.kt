package sushi.hardcore.aira.utils

object StringUtils {
    fun beautifyFingerprint(fingerprint: String): String {
        val newFingerprint = StringBuilder(fingerprint.length+7)
        for (i in 0..fingerprint.length-8 step 4) {
            newFingerprint.append(fingerprint.slice(i until i+4)+" ")
        }
        newFingerprint.append(fingerprint.slice(fingerprint.length-4 until fingerprint.length))
        return newFingerprint.toString()
    }
}