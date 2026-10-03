package jadx.tests.integration.enums

object TestEnumsWithTernaryFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.enums;

public class TestEnumsWithTernaryFixture {

	public enum TestCls {
		FIRST(useNumber() ? "1" : "A"),
		SECOND(useNumber() ? "2" : "B"),
		ANY(useNumber() ? "1" : "2");

		private final String str;

		TestCls(String str) {
			this.str = str;
		}

		public String getStr() {
			return str;
		}

		public static boolean useNumber() {
			return false;
		}
	}
}
"""
}
