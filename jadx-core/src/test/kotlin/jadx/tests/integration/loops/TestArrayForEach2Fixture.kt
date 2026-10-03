package jadx.tests.integration.loops

object TestArrayForEach2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

public class TestArrayForEach2Fixture {

	public static class TestCls {
		public void test(String str) {
			for (String s : str.split("\n")) {
				String t = s.trim();
				if (t.length() > 0) {
					System.out.println(t);
				}
			}
		}
	}
}
"""
}
