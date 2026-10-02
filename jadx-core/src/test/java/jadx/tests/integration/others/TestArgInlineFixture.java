package jadx.tests.integration.others;

public class TestArgInlineFixture {

	public static class TestCls {

		public void test(int a) {
			while (a < 10) {
				int b = a + 1;
				a = b;
			}
		}
	}
}
