package jadx.tests.integration.others

object TestDefConstructorWithAnnotationFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class TestDefConstructorWithAnnotationFixture {

	public static class TestCls {
		@AnnotationTest
		public TestCls() {
		}

		@Target(ElementType.CONSTRUCTOR)
		@Retention(RetentionPolicy.RUNTIME)
		public @interface AnnotationTest {
		}
	}
}
"""
}
