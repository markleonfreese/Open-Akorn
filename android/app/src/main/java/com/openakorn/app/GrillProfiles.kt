package com.openakorn.app

import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class GrillProfileTransport(val storageKey: String) {
    WIFI_TCP("wifi"),
    BLUETOOTH("ble"),
}

data class GrillProfile(
    val name: String,
    val transport: GrillProfileTransport,
    val endpoint: String,
) {
    fun isValid(): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.length > MAX_NAME_LENGTH) return false
        if (name != trimmedName || name.any { it == '\n' || it == '\r' || it == '\t' }) return false
        return when (transport) {
            GrillProfileTransport.WIFI_TCP -> PrivateAddress.parse(endpoint) != null
            GrillProfileTransport.BLUETOOTH -> AkornProtocol.isAkornDeviceName(endpoint)
        }
    }

    fun refersToSameConnection(other: GrillProfile): Boolean {
        if (transport != other.transport) return false
        return when (transport) {
            GrillProfileTransport.WIFI_TCP -> {
                val first = PrivateAddress.parse(endpoint) ?: return false
                val second = PrivateAddress.parse(other.endpoint) ?: return false
                first.contentEquals(second)
            }
            GrillProfileTransport.BLUETOOTH -> endpoint == other.endpoint
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 32
    }
}

/** Local-only profile persistence. The app disables Android backup in its manifest. */
class LocalGrillProfiles(private val preferences: SharedPreferences) {
    fun all(): List<GrillProfile> = GrillProfileCodec.decode(preferences.getString(KEY, "") ?: "")

    fun add(profile: GrillProfile): Boolean {
        if (!profile.isValid()) return false
        val profiles = all()
        if (profiles.size >= MAX_PROFILES) return false
        val duplicate = profiles.any { it.refersToSameConnection(profile) }
        if (duplicate) return false
        preferences.edit().putString(KEY, GrillProfileCodec.encode(profiles + profile)).apply()
        return true
    }

    fun remove(profile: GrillProfile): Boolean {
        val profiles = all()
        val remaining = profiles.filterNot { it == profile }
        if (remaining.size == profiles.size) return false
        preferences.edit().putString(KEY, GrillProfileCodec.encode(remaining)).apply()
        return true
    }

    companion object {
        const val MAX_PROFILES = 12
        private const val KEY = "saved_grill_profiles_v1"
    }
}

object GrillProfileCodec {
    fun encode(profiles: List<GrillProfile>): String = profiles
        .take(LocalGrillProfiles.MAX_PROFILES)
        .filter(GrillProfile::isValid)
        .joinToString("\n") { profile ->
            listOf(profile.name, profile.transport.storageKey, profile.endpoint)
                .joinToString("|") { encodeField(it) }
        }

    fun decode(serialized: String): List<GrillProfile> {
        val result = mutableListOf<GrillProfile>()
        for (line in serialized.lineSequence()) {
            if (result.size >= LocalGrillProfiles.MAX_PROFILES) break
            val fields = line.split('|').mapNotNull(::decodeField)
            if (fields.size != 3) continue
            val transport = GrillProfileTransport.values().firstOrNull { it.storageKey == fields[1] } ?: continue
            val profile = GrillProfile(fields[0], transport, fields[2])
            if (profile.isValid() && result.none { it.refersToSameConnection(profile) }) result += profile
        }
        return result
    }

    private fun encodeField(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String? = try {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
