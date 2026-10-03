package jadx.tests.integration.deobf

object TestDontRenameClspOverriddenMethodFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.deobf;

public class TestDontRenameClspOverriddenMethodFixture {

	public static class TestCls {

		public static class A implements Runnable {
			@Override
			public void run() {
			}
		}

		public static class B extends A {
			@Override
			public void run() {
			}
		}
	}
}
"""
}
