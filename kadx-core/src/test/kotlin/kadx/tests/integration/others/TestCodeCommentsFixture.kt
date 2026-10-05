package kadx.tests.integration.others

object TestCodeCommentsFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestCodeCommentsFixture {

	@SuppressWarnings("FieldCanBeLocal")
	public static class TestCls {
		private int intField = 5;

		public static class A {
		}

		public int test() {
			System.out.println("Hello");
			System.out.println("comment");
			return intField;
		}
	}
}
"""
}
