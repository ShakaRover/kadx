package jadx.tests.integration.rename

object TestRenameEnumFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.rename;

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
