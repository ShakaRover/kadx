package jadx.tests.integration.inner;

public class TestAnonymousClass21Fixture {

	@SuppressWarnings("Convert2Lambda")
	public static class TestCls {
		public void test() {
			String str = "str";
			new Thread(new Runnable() {
				@Override
				public void run() {
					System.out.println(str);
				}
			}).start();
		}
	}
}
