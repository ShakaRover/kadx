package kadx.tests.integration.inner

object TestAnonymousClass6Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestAnonymousClass6Fixture {

	public static class TestCls {
		public Runnable test(final double d) {
			return new Runnable() {
				public void run() {
					System.out.println(d);
				}
			};
		}
	}
}
"""
}
