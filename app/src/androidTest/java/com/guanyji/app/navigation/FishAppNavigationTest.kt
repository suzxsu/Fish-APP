package com.guanyji.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guanyji.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishAppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appOpensOnOverviewWithoutChangingTabs() {
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithText("添加鱼缸后，这里会汇总你的养护安排。").assertIsDisplayed()
    }
}
