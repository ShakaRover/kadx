package jadx.tests.integration.inner;

public class TestInnerClassSyntheticConstructorFixture {

	private class TestCls {
		private int mth() {
			return 1;
		}
	}

	public int call() {
		return new TestCls().mth();
	}
}
