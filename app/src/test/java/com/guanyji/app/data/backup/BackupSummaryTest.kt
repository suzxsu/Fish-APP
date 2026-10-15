package com.guanyji.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSummaryTest {
    @Test fun recordTotalIncludesEveryExportedRecordCategory() {
        val summary = BackupSummary(
            createdAt = 1L,
            tanks = 2,
            fish = 3,
            growthRecords = 4,
            feedingRecords = 5,
            waterQualityRecords = 6,
            waterChangeRecords = 7,
            careRecords = 8,
            photos = 9,
        )

        assertEquals(30, summary.records)
    }

    @Test fun emptyBackupHasNoInventedRecords() {
        val summary = BackupSummary(1L, 0, 0, 0, 0, 0, 0, 0, 0)

        assertEquals(0, summary.records)
        assertEquals(0, summary.photos)
    }
}
