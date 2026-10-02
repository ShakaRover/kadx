package jadx.tests.integration.generics;

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
