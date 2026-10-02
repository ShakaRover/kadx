package jadx.tests.integration.trycatch;

public class TestNestedTryCatchFixture {

	public static class TestCls {
		public void test() {
			try {
				Thread.sleep(1L);
				try {
					Thread.sleep(2L);
				} catch (InterruptedException ignored) {
					System.out.println(2);
				}
			} catch (Exception ignored) {
				System.out.println(1);
			}
		}
	}
}
