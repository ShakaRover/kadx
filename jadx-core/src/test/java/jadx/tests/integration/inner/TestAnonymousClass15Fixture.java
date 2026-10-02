package jadx.tests.integration.inner;

public class TestAnonymousClass15Fixture {

	public static class TestCls {

		public Thread test(Runnable run) {
			return new Thread(run) {
				@Override
				public void run() {
					System.out.println("run");
					super.run();
				}
			};
		}

		public Thread test2(Runnable run) {
			return new Thread(run) {
				{
					setName("run");
				}

				@Override
				public void run() {
				}
			};
		}
	}
}
