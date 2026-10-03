package jadx.tests.integration.others

object TestStringBuilderEliminationFixture {
	class MyException

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestStringBuilderEliminationFixture {

	public static class MyException extends Exception {
		private static final long serialVersionUID = 4245254480662372757L;

		public MyException(String str, Exception e) {
			super("msg:" + str, e);
		}

		public void method(int k) {
			System.out.println("k=" + k);
		}
	}
}
"""
}
