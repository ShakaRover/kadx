package kadx.tests.integration.android

object TestRFieldRestore3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.android;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class TestRFieldRestore3Fixture {

	public static class TestCls {

		@T(2131230730)
		public static class A {
			@F(2131230730)
			private int f;

			@M(bind = 2137373737)
			private void mth() {
			}

			@T(2137373737)
			private class D {
			}
		}

		@Retention(RetentionPolicy.RUNTIME)
		@Target(ElementType.TYPE)
		@interface T {
			int value();
		}

		@Retention(RetentionPolicy.RUNTIME)
		@Target({ ElementType.FIELD })
		@interface F {
			int value();
		}

		@Retention(RetentionPolicy.RUNTIME)
		@Target({ ElementType.METHOD })
		@interface M {
			int bind();
		}

		public static class R {
		}
	}
}
"""
}
