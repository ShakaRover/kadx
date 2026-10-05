package kadx.tests.integration.switches

object TestSwitchInLoop2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

public class TestSwitchInLoop2Fixture {

	public static class TestCls {
		public boolean test() {
			while (true) {
				switch (call()) {
					case 0:
						return false;
					case 1:
						return true;
				}
			}
		}

		private int call() {
			return 0;
		}
	}
}
"""
}
