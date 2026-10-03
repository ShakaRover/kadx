package jadx.tests.integration.variables

object TestVariablesInInlinedAssignFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.variables;

public class TestVariablesInInlinedAssignFixture {

	public static class TestCls {
		public final int test(final char[] s) {
			int i;
			for (i = 0; i < s.length; i++) {
				final char c = s[i];
				if (c != 'a' && c != 'b') {
					break;
				}
			}
			return i;
		}
	}
}
"""
}
