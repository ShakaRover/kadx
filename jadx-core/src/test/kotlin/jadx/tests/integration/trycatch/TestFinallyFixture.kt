package jadx.tests.integration.trycatch

object TestFinallyFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.trycatch;

public class TestFinallyFixture {

	public static class TestCls {
		private static final String DISPLAY_NAME = "name";

		String test(Context context, Object uri) {
			Cursor cursor = null;
			try {
				String[] projection = { DISPLAY_NAME };
				cursor = context.query(uri, projection);
				int columnIndex = cursor.getColumnIndexOrThrow(DISPLAY_NAME);
				cursor.moveToFirst();
				return cursor.getString(columnIndex);
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}
		}

		private class Context {
			public Cursor query(Object o, String[] s) {
				return null;
			}
		}

		private class Cursor {
			public void close() {
			}

			public void moveToFirst() {
			}

			public int getColumnIndexOrThrow(String s) {
				return 0;
			}

			public String getString(int i) {
				return null;
			}
		}
	}
}
"""
}
