package jadx.tests.integration.others

object TestStringBuilderElimination4NegFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestStringBuilderElimination4NegFixture {

	public static class TestCls<K, V> {
		private K k;
		private V v;

		public String test() {
			StringBuilder sb = new StringBuilder();
			sb.append(k);
			sb.append('=');
			sb.append(v);
			return sb.toString();
		}
	}
}
"""
}
