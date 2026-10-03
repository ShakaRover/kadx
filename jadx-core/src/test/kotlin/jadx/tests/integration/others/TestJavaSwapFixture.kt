package jadx.tests.integration.others

object TestJavaSwapFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestJavaSwapFixture {

	@SuppressWarnings("StringBufferReplaceableByString")
	public static class TestCls {
		private Iterable<String> field;

		@Override
		public String toString() {
			String string = String.valueOf(this.field);
			return new StringBuilder(8 + String.valueOf(string).length())
					.append("concat(").append(string).append(")")
					.toString();
		}
	}
}
"""
}
