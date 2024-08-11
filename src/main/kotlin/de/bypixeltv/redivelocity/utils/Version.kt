package de.bypixeltv.redivelocity.utils

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
data class Version @Inject constructor(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val tag: String? = null
) : Comparable<Version> {
    companion object {
        fun fromString(versionString: String): Version {
            // Preprocess the versionString to remove any known incorrect prefixes or formats
            val cleanVersionString = versionString.removePrefix("Optional[").removeSuffix("]").trim()
            val parts = cleanVersionString.split("-", limit = 2)
            val numbers = parts[0].split(".")
            val tag = if (parts.size > 1) parts[1] else null
            return Version(numbers[0].toInt(), numbers.getOrNull(1)?.toInt() ?: 0, numbers.getOrNull(2)?.toInt() ?: 0, tag)
        }
    }

    override fun compareTo(other: Version): Int {
        if (this.major != other.major) return this.major - other.major
        if (this.minor != other.minor) return this.minor - other.minor
        if (this.patch != other.patch) return this.patch - other.patch
        // Compare tags if numerical parts are equal
        if (this.tag == null && other.tag != null) return 1 // No tag considered newer
        if (this.tag != null && other.tag == null) return -1 // Tag considered older
        if (this.tag != null && other.tag != null) return this.tag.compareTo(other.tag)
        return 0 // Both versions are identical
    }
}