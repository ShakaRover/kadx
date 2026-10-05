package kadx.tests.integration.types

object TestTypeResolver6aFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

public class TestTypeResolver6aFixture {

	public static class TestCls implements Runnable {
		public final Runnable runnable;

		public TestCls(boolean b) {
			this.runnable = b ? this : makeRunnable();
		}

		public Runnable makeRunnable() {
			return new Runnable() {
				@Override
				public void run() {
					// do nothing
				}
			};
		}

		@Override
		public void run() {
			// do nothing
		}
	}
}
"""
}
