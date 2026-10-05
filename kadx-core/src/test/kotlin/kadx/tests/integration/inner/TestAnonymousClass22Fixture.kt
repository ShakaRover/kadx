package kadx.tests.integration.inner

object TestAnonymousClass22Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestAnonymousClass22Fixture {

	public static class TestCls {

		public static class Parent {
			public static Parent test(Class<?> cls) {
				final AnotherClass another = new AnotherClass();
				return new Parent() {
					@Override
					public String func() {
						return another.toString();
					}
				};
			}

			public String func() {
				return "";
			}
		}

		public static class AnotherClass {
		}
	}
}
"""
}
