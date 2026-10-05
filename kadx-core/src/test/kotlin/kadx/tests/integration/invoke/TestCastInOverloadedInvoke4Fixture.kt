package kadx.tests.integration.invoke

object TestCastInOverloadedInvoke4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

public class TestCastInOverloadedInvoke4Fixture {

	public static class TestCls {
		public String test(String str) {
			return str.replace('\n', ' ');
		}
	}
}
"""
}
