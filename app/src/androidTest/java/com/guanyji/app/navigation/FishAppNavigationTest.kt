package com.guanyji.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun allTopLevelDestinationsCanBeOpened() {
        val destinations = listOf(
            "overview" to "今日概览",
            "fish" to "鱼只档案会保存在本机，目前还没有记录。",
            "tanks" to "我的鱼缸",
            "care" to "养护计划",
            "profile" to "个人设置",
        )
        destinations.forEach { (route, title) ->
            composeRule.onNodeWithTag("destination-$route").performClick()
            composeRule.onNodeWithText(title).assertIsDisplayed()
        }
    }
}
