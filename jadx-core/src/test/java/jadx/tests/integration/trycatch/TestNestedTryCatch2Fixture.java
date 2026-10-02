package jadx.tests.integration.trycatch;

public class TestNestedTryCatch2Fixture {

	public static class TestCls {
		public void test() {
			try {
				try {
					call();
					call();
				} catch (Exception e) {
					exc(e);
				}
			} catch (Exception e) {
				exc(e);
			}
		}

		private void call() {
		}

		private void exc(Exception e) {
		}
	}
}
