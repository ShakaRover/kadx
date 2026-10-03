package jadx.tests.integration.rename

object TestAnonymousInlineFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.rename;

public class TestAnonymousInlineFixture {

	public static class TestCls {
		public Runnable test() {
			return new Runnable() {
				@Override
				public void run() {
					System.out.println("run");
				}
			};
		}
	}
}
"""
}
