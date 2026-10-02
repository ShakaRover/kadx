package jadx.tests.integration.loops;

public class TestDoWhileBreakFixture {

	public static class TestCls {

		public int test(int k) throws InterruptedException {
			int i = 3;
			do {
				if (k > 9) {
					i = 0;
					break;
				}
				i++;
			} while (i < 5);

			return i;
		}
	}
}
