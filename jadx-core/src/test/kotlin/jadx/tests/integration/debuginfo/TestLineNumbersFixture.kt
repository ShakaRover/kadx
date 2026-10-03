package jadx.tests.integration.debuginfo

object TestLineNumbersFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.debuginfo;

// Fixture extracted from the TestLineNumbers driver.
// This test asserts absolute source line numbers, so the padding below
// keeps 'TestCls' starting at line 16, exactly as in the original file.
// (pad 1)
// (pad 2)
// (pad 3)
// (pad 4)
// (pad 5)
// (pad 6)
// (pad 7)
// (pad 8)
public class TestLineNumbersFixture {

	public static class TestCls {
		int field;

		public void func() {
		}

		public static class Inner {
			int innerField;

			public void innerFunc() {
			}

			public void innerFunc2() {
				new Runnable() {
					@Override
					public void run() {
					}
				}.run();
			}

			public void innerFunc3() {
			}
		}
	}
}
"""
}
