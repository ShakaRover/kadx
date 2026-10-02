package jadx.tests.integration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class TestAnnotationsRenameDefFixture {

	public static class TestCls {

		@Target(ElementType.METHOD)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface A {
			int value();
		}

		@A(5)
		void test() {
		}
	}
}
