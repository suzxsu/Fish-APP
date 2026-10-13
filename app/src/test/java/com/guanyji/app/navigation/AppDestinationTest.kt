package com.guanyji.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun `has five unique top level routes in product order`() {
        assertEquals(listOf("overview", "fish", "tanks", "care", "profile"), AppDestination.entries.map { it.route })
        assertEquals(AppDestination.entries.size, AppDestination.entries.map { it.route }.distinct().size)
    }
}
