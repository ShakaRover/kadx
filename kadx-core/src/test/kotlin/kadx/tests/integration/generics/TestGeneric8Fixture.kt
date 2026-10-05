package kadx.tests.integration.generics

object TestGeneric8Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.generics;

public class TestGeneric8Fixture {

	public static class TestCls {
		@SuppressWarnings("InnerClassMayBeStatic")
		public class TestNumber<T extends Integer> {
			private final T n;

			public TestNumber(T n) {
				this.n = n;
			}

			public boolean isEven() {
				return n.intValue() % 2 == 0;
			}
		}
	}
}
"""
}
