package jadx.tests.integration.fallback

object TestFallbackModeFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.fallback;

public class TestFallbackModeFixture {

	public static class TestCls {

		public int test(int a) {
			while (a < 10) {
				a++;
			}
			return a;
		}
	}
}
"""
}
