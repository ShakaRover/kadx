package kadx.tests.integration.inner

object TestInnerClass4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestInnerClass4Fixture {

	public static class TestCls {
		public class C {
			public String c;

			private C() {
				this.c = "c";
			}
		}

		public String test() {
			return new C().c;
		}
	}
}
"""
}
