package jadx.tests.integration.annotations

object TestAnnotationsUsageFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class TestAnnotationsUsageFixture {

	public static class TestCls {

		@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
		@Retention(RetentionPolicy.RUNTIME)
		public @interface A {
			Class<?> c();
		}

		@A(c = TestCls.class)
		public static class B {
		}

		public static class C {
			@A(c = B.class)
			public String field;
		}

		@A(c = B.class)
		void test() {
		}

		void test2(@A(c = B.class) Integer value) {
		}
	}
}
"""
}
