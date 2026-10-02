package jadx.tests.integration.inner;

public class TestInnerClass3Fixture {

	public static class TestCls {
		private String c;

		private void setC(String c) {
			this.c = c;
		}

		public class C {
			public String c() {
				setC("c");
				return c;
			}
		}
	}
}
