package jadx.tests.integration.types

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue 1197：`Cursor` 变量的类型还原应保留为 `Cursor`，而不是被推断为 `AutoCloseable`。
 */
class TestTypeResolver17 : SmaliTest() {
	// @formatter:off
	/*
		private static String test(Context context, Uri uri, String str, String str2) {
			Cursor cursor = null;
			try {
				cursor = context.getContentResolver().query(uri, new String[]{str}, null, null, null);
				if (cursor.moveToFirst() && !cursor.isNull(0)) {
					return cursor.getString(0);
				}
				closeQuietly(cursor);
				return str2;
			} catch (Exception e) {
				Log.w("DocumentFile", "Failed query: " + e);
				return str2;
			} finally {
				closeQuietly(cursor);
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("Cursor cursorQuery = null;")
			.doesNotContain("(AutoCloseable autoCloseable = ")
	}
}
