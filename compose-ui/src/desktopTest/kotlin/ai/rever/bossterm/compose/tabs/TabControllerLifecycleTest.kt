package ai.rever.bossterm.compose.tabs

import ai.rever.bossterm.compose.settings.TerminalSettings
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Verifies the structured concurrency contract between a host-provided parent
 * scope and the terminal tab scopes created by [TabController].
 *
 * The key invariants under test:
 * 1. Parent cancellation propagates to every child tab scope.
 * 2. A single tab's cancellation does not propagate to the parent or siblings.
 * 3. Without a parent scope, existing standalone behavior is preserved.
 */
class TabControllerLifecycleTest {

    @Test
    fun `parent scope cancellation propagates to tab scopes`() = runBlocking {
        val parentJob = Job()
        val parentScope = CoroutineScope(parentJob)

        val controller = TabController(
            settings = TerminalSettings(),
            onLastTabClosed = {},
            parentScope = parentScope
        )

        val tab = controller.createRemoteSession(title = "Test", feedsStream = false)
        assertTrue(tab.coroutineScope.isActive, "Tab scope should be active after creation")

        // Cancel the host-provided parent scope
        parentScope.cancel()

        // The child scope should now be cancelled through structured concurrency
        assertFalse(tab.coroutineScope.isActive, "Tab scope must be cancelled when parent is cancelled")
    }

    @Test
    fun `cancelling one tab does not cancel sibling tabs or parent scope`() = runBlocking {
        val parentJob = Job()
        val parentScope = CoroutineScope(parentJob)

        val controller = TabController(
            settings = TerminalSettings(),
            onLastTabClosed = {},
            parentScope = parentScope
        )

        val tab1 = controller.createRemoteSession(title = "Tab 1", feedsStream = false)
        val tab2 = controller.createRemoteSession(title = "Tab 2", feedsStream = false)

        assertTrue(tab1.coroutineScope.isActive)
        assertTrue(tab2.coroutineScope.isActive)
        assertTrue(parentScope.isActive)

        // Cancel just the first tab
        tab1.coroutineScope.cancel()

        // Tab 1 should be cancelled
        assertFalse(tab1.coroutineScope.isActive, "Tab 1 scope was not cancelled")

        // Sibling tab and parent should remain active because of SupervisorJob
        assertTrue(tab2.coroutineScope.isActive, "Sibling tab was incorrectly cancelled")
        assertTrue(parentScope.isActive, "Parent scope was incorrectly cancelled")
    }

    @Test
    fun `controller functions normally without a parent scope`() = runBlocking {
        // Backward compatibility: no parentScope means standalone behavior
        val controller = TabController(
            settings = TerminalSettings(),
            onLastTabClosed = {},
            parentScope = null
        )

        val tab = controller.createRemoteSession(title = "Test", feedsStream = false)
        assertTrue(tab.coroutineScope.isActive, "Tab scope should be active after creation")

        // Manually cancel the tab (standalone lifecycle)
        tab.coroutineScope.cancel()
        assertFalse(tab.coroutineScope.isActive, "Tab scope should be cancelled after manual cancel")
    }
}
