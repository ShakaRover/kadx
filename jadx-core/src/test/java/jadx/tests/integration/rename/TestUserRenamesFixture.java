package jadx.tests.integration.rename;

public class TestUserRenamesFixture {

	@SuppressWarnings({ "FieldCanBeLocal", "FieldMayBeFinal" })
	public static class TestCls {
		private int intField = 5;

		public static class A {
		}

		public int test(int x) {
			int y = x + "test".length();
			System.out.println(y);
			int z = y + 1;
			System.out.println(z);
			return z;
		}
	}
}
