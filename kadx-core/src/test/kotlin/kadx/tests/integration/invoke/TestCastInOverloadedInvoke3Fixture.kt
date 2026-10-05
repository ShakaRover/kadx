package kadx.tests.integration.invoke

object TestCastInOverloadedInvoke3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

import java.util.List;

public class TestCastInOverloadedInvoke3Fixture {

	public static class OuterCls {
		static int c = 0;

		public static void call(String str) {
			c = 1;
		}

		public static void call(List<String> list) {
			c = 10;
		}
	}

	public static class TestCls {
		public void test() {
			OuterCls.call((String) null);
		}
	}
}
"""
}
