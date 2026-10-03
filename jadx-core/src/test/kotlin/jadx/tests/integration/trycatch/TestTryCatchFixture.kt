package jadx.tests.integration.trycatch

object TestTryCatchFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.trycatch;

public class TestTryCatchFixture {

	public static class TestCls {
		public void f() {
			try {
				Thread.sleep(50L);
			} catch (InterruptedException e) {
				// ignore
			}
		}
	}
}
"""
}
