package kadx.tests.integration.loops

object TestComplexWhileLoopFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

public class TestComplexWhileLoopFixture {

	public static class TestCls {
		public static String test(String[] arr) {
			int index = 0;
			int length = arr.length;
			String str;
			while ((str = arr[index]) != null) {
				if (str.length() == 1) {
					return str.trim();
				}
				if (++index >= length) {
					index = 0;
				}
			}
			System.out.println("loop end");
			return "";
		}
	}
}
"""
}
