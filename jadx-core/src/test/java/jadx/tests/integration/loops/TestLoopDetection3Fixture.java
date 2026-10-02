package jadx.tests.integration.loops;

public class TestLoopDetection3Fixture {

	public static class TestCls {

		public void test(TestCls parent, int pos) {
			Object item;
			while (--pos >= 0) {
				item = parent.get(pos);
				if (item instanceof String) {
					func((String) item);
					return;
				}
			}
		}

		private Object get(int pos) {
			return null;
		}

		private void func(String item) {
		}
	}
}
