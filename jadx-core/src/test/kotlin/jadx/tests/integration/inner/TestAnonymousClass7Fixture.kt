package jadx.tests.integration.inner

object TestAnonymousClass7Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inner;

public class TestAnonymousClass7Fixture {

	public static class TestCls {
		public static Runnable test(final double d) {
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
