package jadx.tests.integration.inner

object TestAnonymousClass13Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inner;

public class TestAnonymousClass13Fixture {

	public static class TestCls {

		public void test() {
			new TestCls() {
			};
		}
	}
}
"""
}
