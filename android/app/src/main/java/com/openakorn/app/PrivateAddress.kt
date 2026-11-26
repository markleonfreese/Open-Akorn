package com.openakorn.app

/** Parses only explicit RFC1918 IPv4 addresses; it never resolves names or scans a network. */
object PrivateAddress {
    fun parse(value: String): ByteArray? {
        val parts = value.trim().split('.')
        if (parts.size != 4) return null
        val octets = parts.map { part ->
            part.toIntOrNull()?.takeIf { octet -> octet in 0..255 } ?: return null
        }
        val isPrivate = octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
        return if (isPrivate) ByteArray(4) { octets[it].toByte() } else null
    }
}
