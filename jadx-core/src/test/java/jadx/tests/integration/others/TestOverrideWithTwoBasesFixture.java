package jadx.tests.integration.others;

public class TestOverrideWithTwoBasesFixture {

	public static class TestCls {
		public abstract static class BaseClass {
			public abstract int a();
		}

		public interface I {
			int a();
		}

		public static class Cls extends BaseClass implements I {
			@Override
			public int a() {
				return 2;
			}
		}
	}
}
