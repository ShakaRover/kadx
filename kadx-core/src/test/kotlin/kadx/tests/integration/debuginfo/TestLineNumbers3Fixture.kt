package kadx.tests.integration.debuginfo

object TestLineNumbers3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.debuginfo;

// Fixture extracted from the TestLineNumbers3 driver.
// The test asserts absolute source line numbers, so the padding below
// keeps 'TestCls' starting at line 12, exactly as in the original file.
// (pad 1)
// (pad 2)
// (pad 3)
// (pad 4)
public class TestLineNumbers3Fixture {

	public static class TestCls extends Exception {

		public TestCls(final Object message) {
			super((message == null) ? "" : message.toString());
			/*
			 * comment to increase line number in return instruction
			 * -
			 * -
			 * -
			 * -
			 * -
			 * -
			 * -
			 * -
			 * -
			 * -
			 */
		}
	}
}
"""
}
