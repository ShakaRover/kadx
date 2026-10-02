package jadx.tests.integration.switches;

public class TestSwitchReturnFromCaseFixture {

	public static class TestCls {
		public void test(int a) {
			if (a > 1000) {
				return;
			}
			String s = null;
			switch (a % 10) {
				case 1:
					s = "1";
					break;
				case 2:
					s = "2";
					break;
				case 3:
				case 4:
					s = "4";
					break;
				case 5:
					break;
				case 6:
					return;
			}
			if (s == null) {
				s = "5";
			}
			System.out.println(s);
		}
	}
}
