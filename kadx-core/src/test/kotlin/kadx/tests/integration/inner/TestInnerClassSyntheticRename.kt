package kadx.tests.integration.inner

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
