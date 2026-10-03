package jadx.tests.integration.rename

object TestConstReplaceFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.rename;

public class TestConstReplaceFixture {

	public static class TestCls {
		public static final String CONST = "SOME_CONST";

		public String test() {
			return CONST;
		}
	}
}
"""
}
