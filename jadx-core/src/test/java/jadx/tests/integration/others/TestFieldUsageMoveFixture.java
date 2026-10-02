package jadx.tests.integration.others;

public class TestFieldUsageMoveFixture {

	public static class TestCls {
		public static void test(Object obj) {
			if (obj instanceof Boolean) {
				System.out.println("Boolean: " + obj);
			}
			if (obj instanceof Float) {
				System.out.println("Float: " + obj);
			}
		}
	}
}
