package jadx.tests.integration.types;

public class TestTypeResolver3Fixture {

	@SuppressWarnings("UseCompareMethod")
	public static class TestCls {

		public int test(String s1, String s2) {
			int cmp = s2.compareTo(s1);
			if (cmp != 0) {
				return cmp;
			}
			return s1.length() == s2.length() ? 0 : s1.length() < s2.length() ? -1 : 1;
		}
	}
}
