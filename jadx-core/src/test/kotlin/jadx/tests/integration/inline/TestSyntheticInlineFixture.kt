package jadx.tests.integration.inline

object TestSyntheticInlineFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inline;

public class TestSyntheticInlineFixture {

	public static class TestCls {
		private int f;

		private int func() {
			return -1;
		}

		public class A {
			public int getF() {
				return f;
			}

			public void setF(int v) {
				f = v;
			}

			public int callFunc() {
				return func();
			}
		}
	}
}
"""
}
