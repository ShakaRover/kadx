package jadx.tests.integration.debuginfo;

// Fixture extracted from the TestReturnSourceLine driver.
// The test asserts source line numbers relative to each node, so the
// padding below keeps 'TestCls' starting at line 17, exactly as in the
// original file.
// (pad 1)
// (pad 2)
// (pad 3)
// (pad 4)
// (pad 5)
// (pad 6)
// (pad 7)
// (pad 8)
public class TestReturnSourceLineFixture {

	public static class TestCls {
		public int test1(boolean v) {
			if (v) {
				f();
				return 1;
			}
			f();
			return 0;
		}

		public int test2(int v) {
			if (v == 0) {
				f();
				return v - 1;
			}
			f();
			return v + 1;
		}

		public int test3(int v) {
			if (v == 0) {
				f();
				return v;
			}
			f();
			return v + 1;
		}

		private void f() {
		}
	}
}
