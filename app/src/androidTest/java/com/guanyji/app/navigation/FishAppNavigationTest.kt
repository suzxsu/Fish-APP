package com.guanyji.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.guanyji.app.core.designsystem.rememberLeaveFormHandler
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
        composeRule.onNodeWithText("观鱼记").assertIsDisplayed()
    }

    @Test
    fun switchingTabsShowsRealEmptyStatesAndReturningKeepsOverviewAvailable() {
        composeRule.onNodeWithTag("destination-fish").performClick()
        composeRule.onNodeWithTag("destination-fish").assertIsSelected()
        composeRule.onNodeWithText("我的鱼").assertIsDisplayed()

        composeRule.onNodeWithTag("destination-care").performClick()
        composeRule.onNodeWithTag("destination-care").assertIsSelected()
        composeRule.onNodeWithText("养护计划").assertIsDisplayed()

        composeRule.onNodeWithTag("destination-overview").performClick()
        composeRule.onNodeWithTag("destination-overview").assertIsSelected()
        composeRule.onNodeWithText("今日总览").assertIsDisplayed()
    }

    @Test
    fun dirtyFormRequiresExplicitDiscardForToolbarAndSystemBack() {
        composeRule.setContent {
            var draft by remember { mutableStateOf("") }
            var left by remember { mutableStateOf(false) }
            val leave = rememberLeaveFormHandler(draft.isNotEmpty()) { left = true }
            Column {
                TextField(draft, { draft = it }, Modifier.testTag("draft-field"))
                Button(onClick = leave, modifier = Modifier.testTag("leave-form")) { Text("返回") }
                if (left) Text("已离开")
            }
        }
        composeRule.onNodeWithTag("draft-field").performTextInput("尚未保存")
        composeRule.onNodeWithTag("leave-form").performClick()
        composeRule.onNodeWithText("放弃更改？").assertIsDisplayed()
        composeRule.onNodeWithText("继续编辑").performClick()
        composeRule.onNodeWithTag("draft-field").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("放弃更改？").assertIsDisplayed()
        composeRule.onNodeWithText("放弃更改").performClick()
        composeRule.onNodeWithText("已离开").assertIsDisplayed()
    }
}
