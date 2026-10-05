package kadx.tests.integration.inner

object TestReplaceConstsInAnnotations2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class TestReplaceConstsInAnnotations2Fixture {

	public static class TestCls {
		@Target(ElementType.TYPE)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface A {
			int[] value();
		}

		@A(C.INT_CONST)
		public static class C {
			public static final int INT_CONST = 23412342;
		}

		@A({ C.INT_CONST, C2.INT_CONST })
		public static class C2 {
			public static final int INT_CONST = 34563456;
		}
	}
}
"""
}
