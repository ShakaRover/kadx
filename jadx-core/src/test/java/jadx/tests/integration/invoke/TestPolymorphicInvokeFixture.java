package jadx.tests.integration.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class TestPolymorphicInvokeFixture {

	public static class TestCls {
		public String func(int a, int c) {
			return String.valueOf(a + c);
		}

		public String test() {
			try {
				MethodType methodType = MethodType.methodType(String.class, Integer.TYPE, Integer.TYPE);
				MethodHandle methodHandle = MethodHandles.lookup().findVirtual(TestCls.class, "func", methodType);
				return (String) methodHandle.invoke(this, 1, 2);
			} catch (Throwable e) {
				fail("", e);
				return null;
			}
		}

		public void check() {
			assertThat(test()).isEqualTo("3");
		}
	}
}
