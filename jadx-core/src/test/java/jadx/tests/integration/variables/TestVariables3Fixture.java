package jadx.tests.integration.variables;

public class TestVariables3Fixture {

	public static class TestCls {
		String test(Object s) {
			int i;
			if (s == null) {
				i = 2;
			} else {
				i = 3;
				s = null;
			}
			return s + " " + i;
		}
	}
}
