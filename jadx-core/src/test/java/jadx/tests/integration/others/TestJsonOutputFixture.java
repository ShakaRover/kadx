package jadx.tests.integration.others;

import java.util.List;

public class TestJsonOutputFixture {

	public static class TestCls {
		private final String prefix = "list: ";

		static {
			System.out.println("test");
		}

		public void test(boolean b, List<String> list) {
			if (b) {
				System.out.println(prefix + list);
			}
		}

		public static class Inner implements Runnable {
			@Override
			public void run() {
				System.out.println("run");
			}
		}
	}
}
