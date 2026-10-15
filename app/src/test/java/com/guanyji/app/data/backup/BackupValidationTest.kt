package com.guanyji.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupValidationTest {
    private val hashes = mapOf("db/guanyji.db" to "db-hash", "photos/original/a" to "photo-hash")
    private val required = setOf("db/guanyji.db")

    @Test fun rejectsDamagedPackageChecksum() {
        assertInvalid("checksum-mismatch", hashes, hashes + ("photos/original/a" to "damaged"))
    }

    @Test fun rejectsMissingPhotoReferencedByManifest() {
        assertInvalid("missing-photo", hashes, mapOf("db/guanyji.db" to "db-hash"), setOf("photos/original/a"))
    }

    @Test fun rejectsFutureFormatWithoutAttemptingImport() {
        val result = validateBackupEnvelope(2, 1, 8, 8, hashes, hashes, required, emptySet())
        assertEquals(BackupValidationResult.Invalid("future-version"), result)
    }

    @Test fun rejectsMissingRequiredDatabase() {
        assertInvalid("missing-required-file", emptyMap(), emptyMap())
    }

    @Test fun interruptedRestoreIsRecoveredOnlyAfterSafetyMarkerWasWritten() {
        assertEquals(false, needsInterruptedRestoreRecovery(false))
        assertEquals(true, needsInterruptedRestoreRecovery(true))
    }

    @Test fun reportsInsufficientStorageBeforeStartingReplacement() {
        assertEquals(false, hasEnoughStorage(99, 100))
        assertEquals(true, hasEnoughStorage(100, 100))
    }

    private fun assertInvalid(
        reason: String,
        expected: Map<String, String>,
        actual: Map<String, String>,
        photos: Set<String> = emptySet(),
    ) {
        val result = validateBackupEnvelope(1, 1, 8, 8, expected, actual, required, photos)
        assertEquals(BackupValidationResult.Invalid(reason), result)
    }
}
