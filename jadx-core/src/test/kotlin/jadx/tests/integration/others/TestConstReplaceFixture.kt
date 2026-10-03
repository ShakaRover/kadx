package jadx.tests.integration.others

object TestConstReplaceFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestConstReplaceFixture {

	public static class TestCls {
		public static final String CONST_VALUE = "string";

		public String test() {
			return CONST_VALUE;
		}
	}
}
"""
}
