package jadx.tests.integration.annotations;

public class TestAnnotationsFixture {

	public static class TestCls {
		private @interface A {
			int a();
		}

		@A(a = -1)
		public void methodA1() {
		}

		@A(a = -253)
		public void methodA2() {
		}

		@A(a = -11253)
		public void methodA3() {
		}

		private @interface V {
			boolean value();
		}

		@V(false)
		public void methodV() {
		}

		private @interface D {
			float value() default 1.1f;
		}

		@D
		public void methodD() {
		}
	}
}
