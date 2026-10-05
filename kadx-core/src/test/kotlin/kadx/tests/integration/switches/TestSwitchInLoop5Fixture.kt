package kadx.tests.integration.switches

object TestSwitchInLoop5Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

public class TestSwitchInLoop5Fixture {

	public static class TestCls {
		private static int test(int r) {
			int i;
			while (true) {
				switch (r) {
					case 42:
						i = 32;
						break;
					case 52:
						i = 42;
						break;
					default:
						System.out.println("Default switch case");
						return 1;
				}
				r = i;
			}
		}
	}
}
"""
}
