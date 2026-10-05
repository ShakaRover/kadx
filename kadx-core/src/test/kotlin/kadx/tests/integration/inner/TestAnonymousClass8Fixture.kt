package kadx.tests.integration.inner

object TestAnonymousClass8Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestAnonymousClass8Fixture {

	public static class TestCls {

		public final double d = Math.abs(4);

		public Runnable test() {
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
