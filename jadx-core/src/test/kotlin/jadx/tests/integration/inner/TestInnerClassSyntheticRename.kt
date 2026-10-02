package jadx.tests.integration.inner

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue: #336
 */
@SuppressWarnings("CommentedOutCode")
class TestInnerClassSyntheticRename : SmaliTest() {
	// @formatter:off
	/*
		private class TestCls extends AsyncTask<Uri, Uri, List<Uri>> {
			@Override
			protected List<Uri> doInBackground(Uri... uris) {
				Log.i("MyAsync", "doInBackground");
				return null;
			}

			@Override
			protected void onPostExecute(List<Uri> uris) {
				Log.i("MyAsync", "onPostExecute");
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("List<Uri> doInBackground(Uri... uriArr) {")
			.containsOne("void onPostExecute(List<Uri> list) {")
			.doesNotContain("synthetic")
	}
}
