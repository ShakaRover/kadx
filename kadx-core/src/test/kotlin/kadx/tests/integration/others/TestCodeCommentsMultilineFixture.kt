package kadx.tests.integration.others

object TestCodeCommentsMultilineFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestCodeCommentsMultilineFixture {

	public static class TestCls {
		public int test(boolean z) {
			if (z) {
				System.out.println("z");
				return 1;
			}
			return 3;
		}
	}
}
"""
}
