package jadx.tests.integration.conditions

object TestConditions12Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestConditions12Fixture {

	public static class TestCls {
		static boolean autoStop = true;
		static boolean qualityReading = false;
		static int lastValidRaw = -1;

		public static void main(String[] args) throws Exception {
			int a = 5;
			int b = 30;
			dataProcess(a, b);
		}

		public static void dataProcess(int raw, int quality) {
			if (quality >= 10 && raw != 0) {
				System.out.println("OK" + raw);
				qualityReading = false;
			} else if (raw == 0 || quality < 6 || !qualityReading) {
				System.out.println("Not OK" + raw);
			} else {
				System.out.println("Quit OK" + raw);
			}
			if (quality < 30) {
				int timeLeft = 30 - quality;
				if (quality >= 10) {
					System.out.println("Processing" + timeLeft);
				}
			} else {
				System.out.println("Finish Processing");
				if (raw > 0) {
					lastValidRaw = raw;
				}
			}
			if (quality >= 30 && autoStop) {
				System.out.println("Finished");
			}
			if (!autoStop && lastValidRaw > -1 && quality < 10) {
				System.out.println("Finished");
			}
		}
	}
}
"""
}
