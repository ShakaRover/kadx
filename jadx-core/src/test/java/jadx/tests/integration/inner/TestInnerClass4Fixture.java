package jadx.tests.integration.inner;

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
