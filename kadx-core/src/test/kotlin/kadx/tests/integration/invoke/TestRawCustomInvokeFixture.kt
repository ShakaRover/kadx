package kadx.tests.integration.invoke

object TestRawCustomInvokeFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class TestRawCustomInvokeFixture {

	public static class TestCls {

		public static String func(int a, double b) {
			return String.valueOf(a + b);
		}

		private static CallSite staticBootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
			try {
				return new ConstantCallSite(lookup.findStatic(lookup.lookupClass(), name, type));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}

		public String test() {
			try {
				return (String) staticBootstrap(MethodHandles.lookup(), "func",
						MethodType.methodType(String.class, Integer.TYPE, Double.TYPE))
								.dynamicInvoker().invoke(1, 2.0d);
			} catch (Throwable e) {
				fail("", e);
				return null;
			}
		}

		public void check() {
			assertThat(test()).isEqualTo("3.0");
		}
	}
}
"""
}
