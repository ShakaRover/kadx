package jadx.tests.integration.loops;

public class TestSynchronizedInEndlessLoopFixture {

	@SuppressWarnings("BusyWait")
	public static class TestCls {
		int f = 5;

		int test() {
			while (true) {
				synchronized (this) {
					if (f > 7) {
						return 7;
					}
					if (f < 3) {
						return 3;
					}
				}
				try {
					f++;
					Thread.sleep(100L);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
}
