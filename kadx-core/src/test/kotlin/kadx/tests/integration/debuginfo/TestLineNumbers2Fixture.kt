package kadx.tests.integration.debuginfo

object TestLineNumbers2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.debuginfo;

import java.lang.ref.WeakReference;

// Fixture extracted from the TestLineNumbers2 driver.
// The test asserts absolute source line numbers, so the padding below
// keeps 'TestCls' starting at line 14, exactly as in the original file.
// (pad 1)
// (pad 2)
// (pad 3)
// (pad 4)
public class TestLineNumbers2Fixture {

	public static class TestCls {
		private WeakReference<TestCls> f;

		// keep constructor at line 18
		public TestCls(TestCls s) {
		}

		public TestCls test(TestCls s) {
			TestCls store = f != null ? f.get() : null;
			if (store == null) {
				store = new TestCls(s);
				f = new WeakReference<>(store);
			}
			return store;
		}

		public Object test2() {
			return new Object();
		}
	}
}
"""
}
