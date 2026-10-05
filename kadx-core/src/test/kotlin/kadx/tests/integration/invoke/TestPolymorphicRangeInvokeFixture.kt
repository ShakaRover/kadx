package kadx.tests.integration.invoke

object TestPolymorphicRangeInvokeFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class TestPolymorphicRangeInvokeFixture {

	public static class TestCls {
		public String func2(int a, int b, int c, int d, int e, int f) {
			return String.valueOf(a + b + c + d + e + f);
		}

		public String test() {
			try {
				MethodHandles.Lookup lookup = MethodHandles.lookup();
				MethodType methodType = MethodType.methodType(String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE,
						Integer.TYPE, Integer.TYPE);
				MethodHandle methodHandle = lookup.findVirtual(TestCls.class, "func2", methodType);
				return (String) methodHandle.invoke(this, 10, 20, 30, 40, 50, 60);
			} catch (Throwable e) {
				fail("", e);
				return null;
			}
		}

		public void check() {
			assertThat(test()).isEqualTo("210");
		}
	}
}
"""
}
