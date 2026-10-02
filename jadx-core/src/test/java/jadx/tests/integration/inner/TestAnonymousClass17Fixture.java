package jadx.tests.integration.inner;

public class TestAnonymousClass17Fixture {

	public static class TestCls {

		@SuppressWarnings({ "checkstyle:InnerAssignment", "Convert2Lambda" })
		public void test(boolean a, boolean b) {
			String v;
			if (a && (v = get(b)) != null) {
				use(new Runnable() {
					@Override
					public void run() {
						System.out.println(v);
					}
				});
			}
		}

		public String get(boolean a) {
			return a ? "str" : null;
		}

		public void use(Runnable r) {
		}
	}
}
