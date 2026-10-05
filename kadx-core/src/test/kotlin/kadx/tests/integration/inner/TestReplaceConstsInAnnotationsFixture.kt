package kadx.tests.integration.inner

object TestReplaceConstsInAnnotationsFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class TestReplaceConstsInAnnotationsFixture {

	public static class TestCls {
		@Target(ElementType.TYPE)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface A {
			int i();

			float f();
		}

		@A(i = -1, f = C.FLOAT_CONST)
		public static class C {
			public static final float FLOAT_CONST = 3.14f;
		}
	}
}
"""
}
