package jadx.tests.integration.fallback;

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
