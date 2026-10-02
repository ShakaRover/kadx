package jadx.tests.integration.trycatch;

public class TestTryCatch2Fixture {

	public static class TestCls {
		private static final Object OBJ = new Object();

		public static boolean test() {
			try {
				synchronized (OBJ) {
					OBJ.wait(5L);
				}
				return true;
			} catch (InterruptedException e) {
				return false;
			}
		}
	}
}
