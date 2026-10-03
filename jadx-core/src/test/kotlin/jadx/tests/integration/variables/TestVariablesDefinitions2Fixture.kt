package jadx.tests.integration.variables

object TestVariablesDefinitions2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.variables;

import java.util.List;

public class TestVariablesDefinitions2Fixture {

	public static class TestCls {

		public static int test(List<String> list) {
			int i = 0;
			if (list != null) {
				for (String str : list) {
					if (str.isEmpty()) {
						i++;
					}
				}
			}
			return i;
		}
	}
}
"""
}
