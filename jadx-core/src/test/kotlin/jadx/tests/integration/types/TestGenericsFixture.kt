package jadx.tests.integration.types

object TestGenericsFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

public class TestGenericsFixture {

	public static class TestCls<T> {
		public T data;

		public TestCls<T> data(T t) {
			this.data = t;
			return this;
		}
	}
}
"""
}
