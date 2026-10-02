package jadx.tests.integration.variables;

public class TestVariables2Fixture {

	public static class TestCls {
		public Object test(Object s) {
			Object store = s != null ? s : null;
			if (store == null) {
				store = new Object();
				s = store;
			}
			return store;
		}
	}
}
