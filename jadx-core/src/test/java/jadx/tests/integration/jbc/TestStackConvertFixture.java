package jadx.tests.integration.jbc;

public class TestStackConvertFixture {

	@SuppressWarnings({ "UnnecessaryLocalVariable", "CallToPrintStackTrace", "printstacktrace" })
	public static class TestCls {
		public int parseIntDefault(String num, int defaultNum) {
			try {
				int defaultNum2 = Integer.parseInt(num);
				return defaultNum2;
			} catch (NumberFormatException e) {
				System.out.println("Before println");
				e.printStackTrace();
				return defaultNum;
			}
		}
	}
}
