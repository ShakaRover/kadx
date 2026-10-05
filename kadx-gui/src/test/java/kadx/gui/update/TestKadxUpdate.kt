package kadx.gui.update

import kadx.gui.settings.KadxUpdateChannel
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Test updates fetch.
 * All tests disabled because of network requests, run manually on KadxUpdate changes
 */
@Disabled("Network requests")
class TestKadxUpdate {

	@Test
	fun testStableCheck() {
		KadxUpdate("1.5.0").checkForNewRelease(KadxUpdateChannel.STABLE)?.let {
			println("Latest release: $it")
		}
	}

	@Test
	fun testUnstableCheck() {
		KadxUpdate("r2000").checkForNewRelease(KadxUpdateChannel.UNSTABLE)?.let {
			println("Latest unstable: $it")
		}
	}
}
