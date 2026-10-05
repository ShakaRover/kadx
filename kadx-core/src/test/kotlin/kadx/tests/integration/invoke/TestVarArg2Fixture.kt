package kadx.tests.integration.invoke

object TestVarArg2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

public class TestVarArg2Fixture {

	@SuppressWarnings("ConstantConditions")
	public static class TestCls {
		protected static boolean b1;
		protected static final boolean IS_VALID = b1 && isValid("test");

		private static boolean isValid(String... string) {
			return false;
		}
	}
}
"""
}
