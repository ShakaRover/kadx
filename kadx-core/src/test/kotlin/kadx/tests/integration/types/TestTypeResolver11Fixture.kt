package kadx.tests.integration.types

object TestTypeResolver11Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTypeResolver11Fixture {

	public static class TestCls {
		public Void test(Object... objects) {
			int val = (Integer) objects[0];
			String str = (String) objects[1];
			call(str, str, val, val);
			return null;
		}

		private void call(String a, String b, int... val) {
		}

		private boolean test2(String s1, String... args) {
			String str = Arrays.toString(args);
			return s1.length() + str.length() > 0;
		}

		public void check() {
			test(1, "str");
			assertThat(test2("1", "2", "34")).isTrue();
		}
	}
}
"""
}
