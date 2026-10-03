package jadx.tests.integration.types

object TestTypeResolver6Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

public class TestTypeResolver6Fixture {

	public static class TestCls {
		public final Object obj;

		public TestCls(boolean b) {
			this.obj = b ? this : makeObj();
		}

		public Object makeObj() {
			return new Object();
		}
	}
}
"""
}
