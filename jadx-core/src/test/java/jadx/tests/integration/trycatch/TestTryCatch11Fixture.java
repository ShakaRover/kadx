package jadx.tests.integration.trycatch;

public class TestTryCatch11Fixture {

	public static class TestCls {
		public static class Cursor implements AutoCloseable {
			@Override
			public void close() {
				System.out.println("Closed AutoCloseableResources_First");
			}

			public String getString() {
				return "jfdkelapgfureiqop[]";
			}
		}

		public static String test() {
			try (Cursor cursor = new Cursor()) {
				String value = cursor.getString();
				if (value.startsWith("content://") || !value.startsWith("/") && !value.startsWith("file://")) {
					return null;
				}
				return value;
			} catch (Exception ignore) {
				System.out.println("catch");
			}
			return null;
		}
	}
}
