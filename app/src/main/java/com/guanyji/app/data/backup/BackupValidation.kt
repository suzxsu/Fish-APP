package com.guanyji.app.data.backup

internal sealed interface BackupValidationResult {
    data object Valid : BackupValidationResult
    data class Invalid(val reason: String) : BackupValidationResult
}

internal fun validateBackupEnvelope(
    formatVersion: Int,
    supportedFormatVersion: Int,
    databaseVersion: Int,
    supportedDatabaseVersion: Int,
    expectedHashes: Map<String, String>,
    actualHashes: Map<String, String>,
    requiredFiles: Set<String>,
    photoReferences: Set<String>,
): BackupValidationResult {
    if (formatVersion > supportedFormatVersion || databaseVersion > supportedDatabaseVersion) {
        return BackupValidationResult.Invalid("future-version")
    }
    if (formatVersion != supportedFormatVersion || databaseVersion != supportedDatabaseVersion) {
        return BackupValidationResult.Invalid("incompatible-version")
    }
    if (!expectedHashes.keys.containsAll(requiredFiles) || !actualHashes.keys.containsAll(requiredFiles)) {
        return BackupValidationResult.Invalid("missing-required-file")
    }
    if (!actualHashes.keys.containsAll(photoReferences)) return BackupValidationResult.Invalid("missing-photo")
    if (actualHashes.keys != expectedHashes.keys) return BackupValidationResult.Invalid("file-list-mismatch")
    if (actualHashes.any { (name, digest) -> expectedHashes[name] != digest }) return BackupValidationResult.Invalid("checksum-mismatch")
    return BackupValidationResult.Valid
}

internal fun needsInterruptedRestoreRecovery(readyMarkerExists: Boolean): Boolean = readyMarkerExists

internal fun hasEnoughStorage(availableBytes: Long, requiredBytes: Long): Boolean = availableBytes >= requiredBytes
