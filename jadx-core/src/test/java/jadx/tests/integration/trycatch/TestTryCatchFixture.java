package jadx.tests.integration.trycatch;

public class TestTryCatchFixture {

	public static class TestCls {
		public void f() {
			try {
				Thread.sleep(50L);
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}
}
