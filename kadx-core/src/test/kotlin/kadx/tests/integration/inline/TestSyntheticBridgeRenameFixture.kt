package kadx.tests.integration.inline

object TestSyntheticBridgeRenameFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inline;

public class TestSyntheticBridgeRenameFixture {

	@SuppressWarnings("InnerClassMayBeStatic")
	public static class TestCls {
		private abstract class Inner<V> {
			public abstract V get(String value);
		}

		public class IntInner extends Inner<Integer> {
			public Integer get(String value) {
				return value.length();
			}
		}

		public void test() {
			IntInner inner = new IntInner();
			call(inner.get("a"));
		}

		private static void call(Integer value) {
		}
	}
}
"""
}
