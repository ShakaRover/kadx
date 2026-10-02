package jadx.tests.integration.loops;

public class TestContinueInLoopFixture {

	public static class TestCls {
		public int f;

		public void test(int[] a, int b) {
			for (int i = 0; i < a.length; i++) {
				int v = a[i];
				if (v < b) {
					a[i]++;
				} else if (v > b) {
					a[i]--;
				} else {
					continue;
				}
				if (i < b) {
					break;
				}
			}
			this.f++;
		}
	}
}
