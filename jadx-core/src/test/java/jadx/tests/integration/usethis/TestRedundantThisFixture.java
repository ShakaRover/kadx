package jadx.tests.integration.usethis;

public class TestRedundantThisFixture {

	public static class TestCls {
		public int field1 = 1;
		public int field2 = 2;

		public boolean f1() {
			return false;
		}

		public int method() {
			f1();
			return field1;
		}

		public void method2(int field2) {
			this.field2 = field2;
		}
	}
}
