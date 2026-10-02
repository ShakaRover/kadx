package jadx.tests.integration.inner;

public class TestAnonymousClass19Fixture {

	@SuppressWarnings({ "Convert2Lambda", "unused" })
	public static class TestCls {

		public void test(boolean a, boolean b) {
			boolean c = a && b;
			use(new Runnable() {
				@Override
				public void run() {
					System.out.println(a + " && " + b + " = " + c);
				}
			});
		}

		public void use(Runnable r) {
		}
	}
}
