package jadx.tests.integration.inner;

import java.util.concurrent.Callable;

public class TestNestedAnonymousClassFixture {

	@SuppressWarnings("Convert2Lambda")
	public static class TestCls {
		public void test() {
			use(new Callable<Runnable>() {
				@Override
				public Runnable call() {
					return new Runnable() {
						@Override
						public void run() {
							System.out.println("run");
						}
					};
				}
			});
		}

		public void testLambda() {
			use(() -> () -> System.out.println("lambda"));
		}

		public void use(Callable<Runnable> r) {
		}
	}
}
