package kadx.tests.integration.inner

object TestInnerClassFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestInnerClassFixture {

	public static class TestCls {
		public class Inner {
			public class Inner2 extends Thread {
			}
		}
	}
}
"""
}
