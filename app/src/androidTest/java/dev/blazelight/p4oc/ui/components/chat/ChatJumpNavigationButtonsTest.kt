package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatJumpNavigationButtonsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bothButtonsShowDescriptionsInNavigationOrderAndHandleClicks() {
        var previousClicks = 0
        var bottomClicks = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            PocketCodeTheme {
                ChatJumpNavigationButtons(
                    showPrevious = true,
                    showBottom = true,
                    hasNewContent = true,
                    onPrevious = { previousClicks++ },
                    onBottom = { bottomClicks++ },
                )
            }
        }

        val previous = composeRule.onNodeWithTag("jump_to_previous_user_button")
        val bottom = composeRule.onNodeWithTag("jump_to_bottom_button")
        previous
            .assertIsDisplayed()
            .assertContentDescriptionEquals(context.getString(R.string.cd_jump_to_previous_user_message))
        bottom
            .assertIsDisplayed()
            .assertContentDescriptionEquals(context.getString(R.string.cd_jump_to_bottom))

        val previousBounds = previous.fetchSemanticsNode().boundsInRoot
        val bottomBounds = bottom.fetchSemanticsNode().boundsInRoot
        assertTrue("Previous action must be before the bottom action", previousBounds.left < bottomBounds.left)

        previous.performClick()
        bottom.performClick()
        composeRule.runOnIdle {
            assertEquals(1, previousClicks)
            assertEquals(1, bottomClicks)
        }
    }

    @Test
    fun previousOnlyShowsAndHandlesPreviousAction() {
        var previousClicks = 0

        composeRule.setContent {
            PocketCodeTheme {
                ChatJumpNavigationButtons(
                    showPrevious = true,
                    showBottom = false,
                    hasNewContent = false,
                    onPrevious = { previousClicks++ },
                    onBottom = {},
                )
            }
        }

        composeRule.onNodeWithTag("jump_to_previous_user_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("jump_to_bottom_button").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, previousClicks) }
    }

    @Test
    fun bottomOnlyShowsAndHandlesBottomAction() {
        var bottomClicks = 0

        composeRule.setContent {
            PocketCodeTheme {
                ChatJumpNavigationButtons(
                    showPrevious = false,
                    showBottom = true,
                    hasNewContent = false,
                    onPrevious = {},
                    onBottom = { bottomClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("jump_to_previous_user_button").assertDoesNotExist()
        composeRule.onNodeWithTag("jump_to_bottom_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, bottomClicks) }
    }
}
