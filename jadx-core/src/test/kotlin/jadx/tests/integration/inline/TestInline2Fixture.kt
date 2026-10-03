package jadx.tests.integration.inline

object TestInline2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inline;

public class TestInline2Fixture {

	public static class TestCls {
		public int test() throws InterruptedException {
			int[] a = new int[] { 1, 2, 4, 6, 8 };
			int b = 0;
			for (int i = 0; i < a.length; i += 2) {
				b += a[i];
			}
			for (long i = b; i > 0; i--) {
				b += i;
			}
			return b;
		}
	}
}
"""
}
