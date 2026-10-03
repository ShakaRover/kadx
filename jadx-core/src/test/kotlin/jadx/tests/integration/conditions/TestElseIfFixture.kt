package jadx.tests.integration.conditions

object TestElseIfFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

@SuppressWarnings("IfCanBeSwitch")
public class TestElseIfFixture {

	public static class TestCls {
		public int testIfElse(String str) {
			int r;
			if (str.equals("a")) {
				r = 1;
			} else if (str.equals("b")) {
				r = 2;
			} else if (str.equals("3")) {
				r = 3;
			} else if (str.equals("$")) {
				r = 4;
			} else {
				r = -1;
				System.out.println();
			}
			r = r * 10;
			return Math.abs(r);
		}
	}
}
"""
}
