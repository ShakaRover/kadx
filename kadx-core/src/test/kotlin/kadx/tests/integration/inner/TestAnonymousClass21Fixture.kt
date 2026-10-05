package kadx.tests.integration.inner

object TestAnonymousClass21Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

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
"""
}
