package jadx.tests.integration.inner

object TestOuterConstructorCallFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inner;

public class TestOuterConstructorCallFixture {

	@SuppressWarnings({ "InnerClassMayBeStatic", "unused" })
	public static class TestCls {
		private TestCls(Inner inner) {
			System.out.println(inner);
		}

		private class Inner {
			private TestCls test() {
				return new TestCls(this);
			}
		}
	}
}
"""
}
