package jadx.tests.integration.switches

object TestSwitchInLoop6Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.switches;

public class TestSwitchInLoop6Fixture {

	public static class TestCls {
		private void test() throws Exception {
			while (true) {
				int n = getN();
				switch (n) {
					case 1:
						n1();
						return;
					case 2:
						n2();
						if (getN() == 3) {
							return;
						}
						break;
					case 3:
						n3();
						return;
					case 4:
						n4();
						return;
					default:
						throw new Exception();
				}
			}
		}
		// Output below:
		// @formatter:off
		/*
			public void function() throws Exception {
				do {
					switch (getN()) {
						case 1:
							n1();
							return;
						case 2:
							n2();
							break;
						case 3:
							n3();
							return;
						case 4:
							n4();
							return;
						default:
							throw new Exception();
					}
				} while (getN() != 3);
			}
		*/
		// @formatter:on

		void n1() {
		}

		void n2() {
		}

		void n3() {
		}

		void n4() {
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
