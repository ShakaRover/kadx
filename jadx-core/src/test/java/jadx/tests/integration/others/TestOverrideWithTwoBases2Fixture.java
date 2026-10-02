package jadx.tests.integration.others;

public class TestOverrideWithTwoBases2Fixture {

	public static class TestCls {
		public interface I {
			int a();
		}

		public abstract static class BaseCls implements I {
		}

		public static class Cls extends BaseCls implements I {
			@Override
			public int a() {
				return 2;
			}
		}
	}
}
