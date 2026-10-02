package jadx.tests.integration.synchronize;

public class TestSynchronized2Fixture {

	@SuppressWarnings("unused")
	public static class TestCls {
		private static synchronized boolean test(Object obj) {
			return obj.toString() != null;
		}
	}
}
