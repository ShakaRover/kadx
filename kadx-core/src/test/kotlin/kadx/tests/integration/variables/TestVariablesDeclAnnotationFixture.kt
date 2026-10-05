package kadx.tests.integration.variables

object TestVariablesDeclAnnotationFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.variables;

public class TestVariablesDeclAnnotationFixture {

	public abstract static class TestCls {
		public int test(String str, int i) {
			return i;
		}

		public abstract int test2(String str);
	}
}
"""
}
