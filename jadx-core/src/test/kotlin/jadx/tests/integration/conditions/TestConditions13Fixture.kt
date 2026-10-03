package jadx.tests.integration.conditions

object TestConditions13Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestConditions13Fixture {

	public static class TestCls {
		static boolean qualityReading;

		public static void dataProcess(int raw, int quality) {
			if (quality >= 10 && raw != 0) {
				System.out.println("OK" + raw);
				qualityReading = false;
			} else if (raw == 0 || quality < 6 || !qualityReading) {
				System.out.println("Not OK" + raw);
			} else {
				System.out.println("Quit OK" + raw);
			}
		}
	}
}
"""
}
