package jadx.tests.integration.switches;

public class TestSwitchContinueFixture {

	@SuppressWarnings({ "StringConcatenationInLoop", "DataFlowIssue" })
	public static class TestCls {
		public String test(int a) {
			String s = "";
			while (a > 0) {
				switch (a % 4) {
					case 1:
						s += "1";
						break;
					case 3:
					case 4:
						s += "4";
						break;
					case 5:
						a -= 2;
						continue;
				}
				s += "-";
				a--;
			}
			return s;
		}
	}
}
