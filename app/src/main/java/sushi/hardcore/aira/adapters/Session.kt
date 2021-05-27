package sushi.hardcore.aira.adapters

class Session(
        val sessionId: Int,
        val isContact: Boolean,
        val isVerified: Boolean,
        var seen: Boolean,
        val ip: String?,
        var name: String?,
        var avatar: ByteArray?,
)