package jadx.tests.integration.deobf;

public class TestMthRenameFixture {

	public static class TestCls {

		public abstract static class TestAbstractCls {
			public abstract void a();
		}

		public void test(TestAbstractCls a) {
			a.a();
		}
	}
}
