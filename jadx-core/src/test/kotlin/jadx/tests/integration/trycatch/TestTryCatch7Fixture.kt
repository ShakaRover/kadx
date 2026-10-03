package jadx.tests.integration.trycatch

object TestTryCatch7Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.trycatch;

@SuppressWarnings("checkstyle:printstacktrace")
public class TestTryCatch7Fixture {

	public static class TestCls {
		public Exception test() {
			Exception e = new Exception();
			try {
				Thread.sleep(50);
			} catch (Exception ex) {
				e = ex;
			}
			e.printStackTrace();
			return e;
		}
	}
}
"""
}
