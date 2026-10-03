package jadx.tests.integration.loops

object TestBreakInLoopFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

public class TestBreakInLoopFixture {

	public static class TestCls {
		public int f;

		public void test(int[] a, int b) {
			for (int i = 0; i < a.length; i++) {
				a[i]++;
				if (i < b) {
					break;
				}
			}
			this.f++;
		}
	}
}
"""
}
