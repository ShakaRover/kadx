package kadx.tests.integration.conditions

object TestNestedIf2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

public class TestNestedIf2Fixture {

	public static class TestCls {
		static int executedCount = 0;
		static boolean finished = false;
		static int repeatCount = 2;

		static boolean test(float delta, Object object) {
			if (executedCount != repeatCount && isRun(delta, object)) {
				if (finished) {
					return true;
				}
				if (repeatCount == -1) {
					++executedCount;
					action();
					return false;
				}
				++executedCount;
				if (executedCount >= repeatCount) {
					return true;
				}
				action();
			}
			return false;
		}

		public static void action() {
		}

		public static boolean isRun(float delta, Object object) {
			return delta == 0;
		}
	}
}
"""
}
