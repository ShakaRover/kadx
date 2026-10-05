package kadx.tests.integration.others

object TestDeboxing5Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestDeboxing5Fixture {

	@SuppressWarnings("WrapperTypeMayBePrimitive")
	public static class TestCls {
		private static String type;

		public static void test(String[] args) {
			Float f = (float) -47.99;
			Boolean b = args.length == 0;
			Object o = ((b) ? false : f);
			call(o);
		}

		public static void call(Object o) {
			if (o instanceof Boolean) {
				type = "Boolean";
			}
			if (o instanceof Float) {
				type = "Float";
			}
		}

		private static void verify(String[] arr, String str) {
			type = null;
			test(arr);
			assertThat(type).isEqualTo(str);
		}

		public void check() {
			verify(new String[0], "Boolean");
			verify(new String[] { "1" }, "Float");
		}
	}
}
"""
}
