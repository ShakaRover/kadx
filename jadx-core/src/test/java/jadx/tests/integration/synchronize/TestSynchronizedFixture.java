package jadx.tests.integration.synchronize;

public class TestSynchronizedFixture {

	public static class TestCls {
		public boolean f = false;
		public final Object o = new Object();
		public int i = 7;

		public synchronized boolean test1() {
			return this.f;
		}

		public int test2() {
			synchronized (this.o) {
				return this.i;
			}
		}
	}
}
