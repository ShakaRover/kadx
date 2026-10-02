package jadx.tests.integration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class TestAnnotations2Fixture {

	public static class TestCls {

		@Target(ElementType.TYPE)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface A {
			int i();

			float f();
		}
	}
}
