package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.agent.ActionCategory
import com.example.agent.AgentTool
import com.example.agent.PlanDecomposer
import com.example.data.ai.GeminiService
import com.example.data.preferences.JarvisPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app_name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("JARVIS AI", appName)
  }

  @Test
  fun `plan decomposer categorizes messaging as sensitive`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = JarvisPreferences(context)
    val gemini = GeminiService(prefs)
    val decomposer = PlanDecomposer(gemini)

    val plan = decomposer.decomposeLocally("JARVIS, send Priya a message saying we will meet under the tree tomorrow morning")
    assertEquals(1, plan.steps.size)
    val step = plan.steps[0]
    assertEquals(AgentTool.DRAFT_MESSAGE, step.tool)
    assertEquals(ActionCategory.SENSITIVE, step.category)
    assertEquals("Priya", step.target)
    assertTrue(step.payload.contains("we will meet under the tree"))
  }
}
