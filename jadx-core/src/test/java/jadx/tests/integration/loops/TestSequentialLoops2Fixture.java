package jadx.tests.integration.loops;

public class TestSequentialLoops2Fixture {

	@SuppressWarnings({ "unused", "FieldMayBeFinal" })
	public static class TestCls {
		private static char[] lowercases = new char[] { 'a' };

		public static String asciiToLowerCase(String s) {
			char[] c = null;
			int i = s.length();
			while (i-- > 0) {
				char c1 = s.charAt(i);
				if (c1 <= 127) {
					char c2 = lowercases[c1];
					if (c1 != c2) {
						c = s.toCharArray();
						c[i] = c2;
						break;
					}
				}
			}
			while (i-- > 0) {
				if (c[i] <= 127) {
					c[i] = lowercases[c[i]];
				}
			}
			return c == null ? s : new String(c);
		}
	}
}
