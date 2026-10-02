package jadx.tests.integration.trycatch;

public class TestIfInTryCatchFixture {

	public static class TestCls {
		private void test() {
			/*
			 * 1. if in try
			 * 2. then branch is return
			 * 3. after if, there's more blocks inside try
			 * 4. after try, there's more blocks
			 * this will result in if block and below moved out of try
			 */
			try {
				if (getDouble() > 0.5) {
					return;
				}
				System.out.println("after if");
			} catch (Exception e) {
				System.out.println("exception");
			}
			System.out.println("after try");
		}

		private static double getDouble() throws InterruptedException {
			Thread.sleep(50L);
			return Math.random();
		}
	}
}
