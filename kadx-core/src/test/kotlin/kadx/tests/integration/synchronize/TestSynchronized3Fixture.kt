package kadx.tests.integration.synchronize

object TestSynchronized3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.synchronize;

public class TestSynchronized3Fixture {

	public static class TestCls {
		private int x;

		public void f() {
		}

		public void test() {
			while (true) {
				synchronized (this) {
					if (x == 0) {
						throw new IllegalStateException();
					}
					x++;
					if (x == 10) {
						break;
					}
				}
				this.x++;
				f();
			}
		}
	}
}
"""
}
