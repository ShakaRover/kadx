package kadx.tests.integration.others

object TestCodeMetadata2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestCodeMetadata2Fixture {

	public static class TestCls {
		@SuppressWarnings("Convert2Lambda")
		public Runnable test(boolean a) {
			if (a) {
				return new Runnable() {
					@Override
					public void run() {
						System.out.println("test");
					}
				};
			}
			System.out.println("another");
			return empty();
		}

		public static Runnable empty() {
			return new Runnable() {
				@Override
				public void run() {
					// empty
				}
			};
		}
	}
}
"""
}
