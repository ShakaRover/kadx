package kadx.tests.integration.switches

object TestSwitchNoDefaultFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

public class TestSwitchNoDefaultFixture {

	public static class TestCls {
		public void test(int a) {
			String s = null;
			switch (a) {
				case 1:
					s = "1";
					break;
				case 2:
					s = "2";
					break;
				case 3:
					s = "3";
					break;
				case 4:
					s = "4";
					break;
			}
			System.out.println(s);
		}
	}
}
"""
}
