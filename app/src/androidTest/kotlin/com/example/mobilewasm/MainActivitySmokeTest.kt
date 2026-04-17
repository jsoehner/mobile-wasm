package com.example.mobilewasm

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.hamcrest.Matcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val packageName = "com.example.mobilewasm"
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun demoPackageLoadsAndRuns() {
        launchApp()

        val loadButton = device.wait(Until.findObject(By.res(packageName, "btnLoad")), 10_000)
        assertTrue("Load button not found", loadButton != null)
        loadButton!!.click()

        val loadStatus = waitForStatus(
            timeoutMs = 30_000,
            successToken = "ready",
            failureTokens = listOf("Load error", "install failed", "❌")
        )
        assertFalse("Load failed: $loadStatus", loadStatus.contains("❌"))
        assertTrue("Expected module to become ready, status was: $loadStatus", loadStatus.contains("ready"))

        val runButton = device.wait(Until.findObject(By.res(packageName, "btnRun")), 5_000)
        assertTrue("Run button not found", runButton != null)
        runButton!!.click()

        val runStatus = waitForStatus(
            timeoutMs = 20_000,
            successToken = "Run completed",
            failureTokens = listOf("Run error", "❌")
        )
        assertFalse("Run failed: $runStatus", runStatus.contains("❌"))
        assertTrue("Expected successful run status, got: $runStatus", runStatus.contains("Run completed"))

        val output = getText("tvOutput")
        assertTrue("Expected non-empty output", output.isNotBlank())
    }

    private fun launchApp() {
        val context = instrumentation.targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        requireNotNull(launchIntent) { "Launch intent for $packageName not found" }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 10_000)
    }

    private fun waitForStatus(
        timeoutMs: Long,
        successToken: String,
        failureTokens: List<String>
    ): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var status = getText("tvStatus")

        while (SystemClock.elapsedRealtime() < deadline) {
            status = getText("tvStatus")
            if (status.contains(successToken)) return status
            if (failureTokens.any { token -> status.contains(token) }) return status
            SystemClock.sleep(250)
        }

        return status
    }

    private fun getText(viewId: String): String {
        val view = device.wait(Until.findObject(By.res(packageName, viewId)), 5_000)
        return view?.text.orEmpty()
    }
}
