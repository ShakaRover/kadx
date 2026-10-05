package kadx.tests.integration.inner

object TestInnerClassSyntheticConstructorFixture {
	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

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
"""
}
