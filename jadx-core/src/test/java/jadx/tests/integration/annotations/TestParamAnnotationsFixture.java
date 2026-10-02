package jadx.tests.integration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class TestParamAnnotationsFixture {

	public static class TestCls {

		@Target(ElementType.PARAMETER)
		@Retention(RetentionPolicy.RUNTIME)
		public static @interface A {
			int i() default 7;
		}

		void test1(@A int i) {
		}

		void test2(int i, @A int j) {
		}

		void test3(@A(i = 5) int i) {
		}
	}
}
