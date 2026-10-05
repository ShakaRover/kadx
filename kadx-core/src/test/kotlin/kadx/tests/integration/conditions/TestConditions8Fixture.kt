package kadx.tests.integration.conditions

object TestConditions8Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

public class TestConditions8Fixture {

	public static class TestCls {
		private TestCls pager;
		private TestCls listView;

		public void test(TestCls view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			if (!isUsable()) {
				return;
			}
			if (!pager.hasMore()) {
				return;
			}
			if (getLoaderManager().hasRunningLoaders()) {
				return;
			}
			if (listView != null
					&& listView.getLastVisiblePosition() >= pager.size()) {
				showMore();
			}
		}

		private void showMore() {
		}

		private int size() {
			return 0;
		}

		private int getLastVisiblePosition() {
			return 0;
		}

		private boolean hasRunningLoaders() {
			return false;
		}

		private TestCls getLoaderManager() {
			return null;
		}

		private boolean hasMore() {
			return false;
		}

		private boolean isUsable() {
			return false;
		}
	}
}
"""
}
