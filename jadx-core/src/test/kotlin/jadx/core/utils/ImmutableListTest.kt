package jadx.core.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImmutableListTest {

	@Test
	fun lastIndexOfElementOnlyAtIndex0() {
		val arr = arrayOf("a", "b", "c")
		val list = ImmutableList(arr)
		// "a" is at index 0 only; lastIndexOf should return 0
		assertEquals(0, list.lastIndexOf("a"))
	}

	@Test
	fun lastIndexOfElementAtIndex0AndLater() {
		val arr = arrayOf("a", "b", "a")
		val list = ImmutableList(arr)
		// "a" is at index 0 and 2; lastIndexOf should return 2
		assertEquals(2, list.lastIndexOf("a"))
	}

	@Test
	fun lastIndexOfElementNotPresent() {
		val arr = arrayOf("a", "b", "c")
		val list = ImmutableList(arr)
		assertEquals(-1, list.lastIndexOf("z"))
	}

	@Test
	fun lastIndexOfSingleElementList() {
		val arr = arrayOf("only")
		val list = ImmutableList(arr)
		assertEquals(0, list.lastIndexOf("only"))
	}
}
