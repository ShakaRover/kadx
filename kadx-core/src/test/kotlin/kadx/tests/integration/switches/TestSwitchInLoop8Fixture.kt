package kadx.tests.integration.switches

object TestSwitchInLoop8Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

public class TestSwitchInLoop8Fixture {

	public static class TestCls {
		private void test() {
			int i = 0;
			int n = getN();
			while (true) {
				if (i > n) {
					break;
				}
				i++;
				if (n == 5) {
					continue;
				}
				switch (n) {
					case 0: {
						if (n != 1) {
							return;
						}
						break;
					}
					case 1:
						i++;
						break;
					default:
						continue;
				}
				if (i < 2) {
					i++;
				}
			}
			return;
		}

		private int getN() {
			double i = Math.random();
			if (i < 0.25) {
				return 1;
			}
			if (i < 0.5) {
				return 2;
			}
			if (i < 0.75) {
				return 3;
			}
			if (i < 1.0) {
				return 4;
			}
			return -1;
		}
	}
}
"""
}
