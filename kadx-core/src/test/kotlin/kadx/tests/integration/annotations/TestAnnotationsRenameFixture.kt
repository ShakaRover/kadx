package kadx.tests.integration.annotations

object TestAnnotationsRenameFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestAnnotationsRenameFixture {

	public static class TestCls {

		@Target(ElementType.METHOD)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface A {
			int x();
		}

		@A(x = 5)
		void test() {
		}

		public void check() throws NoSuchMethodException {
			Method test = TestCls.class.getDeclaredMethod("test");
			A annotation = test.getAnnotation(A.class);
			assertThat(annotation.x()).isEqualTo(5);
		}
	}
}
"""
}
