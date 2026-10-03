package jadx.tests.integration.others

object TestCodeComments2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestCodeComments2Fixture {

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
