package jadx.tests.integration.others

object TestClassImplementsSignatureFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestClassImplementsSignatureFixture {

	public static class TestCls {
		public abstract static class A<T> implements Comparable<A<T>> {
			T value;
		}
	}
}
"""
}
