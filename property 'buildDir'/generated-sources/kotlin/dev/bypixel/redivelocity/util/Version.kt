/*
 * Copyright (c) 2025.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.bypixel.redivelocity.util

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val tag: String? = null
) : Comparable<Version> {

    companion object {
        fun fromString(versionString: String): Version {
            val cleanVersionString = versionString
                .replace("Optional[", "")
                .replace("]", "")
                .trim()

            val parts = cleanVersionString.split("-", limit = 2)
            val numbers = parts[0].split(".")
            val tag = if (parts.size > 1) parts[1] else null

            val major = numbers.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = numbers.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = numbers.getOrNull(2)?.toIntOrNull() ?: 0

            return Version(major, minor, patch, tag)
        }
    }

    override fun compareTo(other: Version): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        if (patch != other.patch) return patch - other.patch

        return when {
            tag == null && other.tag != null -> 1
            tag != null && other.tag == null -> -1
            tag != null && other.tag != null -> tag.compareTo(other.tag)
            else -> 0
        }
    }
}
