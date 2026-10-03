package jadx.tests.integration.synchronize

object TestSynchronized6Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.synchronize;

public class TestSynchronized6Fixture {

	public static class TestCls {
		private final Object lock = new Object();

		private boolean test(Object obj) {
			synchronized (this.lock) {
				return isA(obj) || isB(obj);
			}
		}

		private boolean isA(Object obj) {
			return false;
		}

		private boolean isB(Object obj) {
			return false;
		}
	}
}
"""
}
