package jadx.tests.integration.loops

object TestIterableForEachFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

public class TestIterableForEachFixture {

	public static class TestCls {
		public String test(Iterable<String> a) {
			StringBuilder sb = new StringBuilder();
			for (String s : a) {
				sb.append(s);
			}
			return sb.toString();
		}
	}
}
"""
}
