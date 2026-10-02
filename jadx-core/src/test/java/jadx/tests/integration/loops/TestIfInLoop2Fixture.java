package jadx.tests.integration.loops;

public class TestIfInLoop2Fixture {

	public static class TestCls {
		public static void test(String str) {
			int len = str.length();
			int at = 0;
			while (at < len) {
				char c = str.charAt(at);
				int endAt = at + 1;
				if (c == 'A') {
					while (endAt < len) {
						c = str.charAt(endAt);
						if (c == 'B') {
							break;
						}
						endAt++;
					}
				}
				at = endAt;
			}
		}
	}
}
