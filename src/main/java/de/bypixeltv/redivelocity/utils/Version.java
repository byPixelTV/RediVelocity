package de.bypixeltv.redivelocity.utils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;

@Getter
@Singleton
public class Version implements Comparable<Version> {
    private final int major;
    private final int minor;
    private final int patch;
    private final String tag;

    @Inject
    public Version(int major, int minor, int patch, String tag) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.tag = tag;
    }

    public static Version fromString(String versionString) {
        // Preprocess the versionString to remove any known incorrect prefixes or formats
        String cleanVersionString = versionString.replace("Optional[", "").replace("]", "").trim();
        String[] parts = cleanVersionString.split("-", 2);
        String[] numbers = parts[0].split("\\.");
        String tag = parts.length > 1 ? parts[1] : null;
        int major = Integer.parseInt(numbers[0]);
        int minor = numbers.length > 1 ? Integer.parseInt(numbers[1]) : 0;
        int patch = numbers.length > 2 ? Integer.parseInt(numbers[2]) : 0;
        return new Version(major, minor, patch, tag);
    }

    @Override
    public int compareTo(Version other) {
        if (this.major != other.major) return this.major - other.major;
        if (this.minor != other.minor) return this.minor - other.minor;
        if (this.patch != other.patch) return this.patch - other.patch;
        // Compare tags if numerical parts are equal
        if (this.tag == null && other.tag != null) return 1; // No tag considered newer
        if (this.tag != null && other.tag == null) return -1; // Tag considered older
        if (this.tag != null) return this.tag.compareTo(other.tag);
        return 0; // Both versions are identical
    }
}