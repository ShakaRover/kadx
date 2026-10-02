package jadx.tests.integration.loops;

public class TestSequentialLoopsFixture {

	public static class TestCls {
		public int test(int a, int b) {
			int c = b;
			int z;

			while (true) {
				z = c + a;
				if (z >= 7) {
					break;
				}
				c = z;
			}

			while ((z = c + a) >= 7) {
				c = z;
			}
			return c;
		}
	}
}
