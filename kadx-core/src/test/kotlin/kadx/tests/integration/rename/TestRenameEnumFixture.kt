package kadx.tests.integration.rename

object TestRenameEnumFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.rename;

public class TestRenameEnumFixture {

	public static class TestCls {

		public enum A implements Runnable {
			ONE {
				@Override
				public void run() {
					System.out.println("ONE");
				}
			},
			TWO {
				@Override
				public void run() {
					System.out.println("TWO");
				}
			};
		}
	}
}
"""
}
