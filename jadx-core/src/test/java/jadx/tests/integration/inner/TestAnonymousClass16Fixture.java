package jadx.tests.integration.inner;

public class TestAnonymousClass16Fixture {

	public static class TestCls {

		public Something test() {
			Something a = new Something() {
				{
					put("a", "b");
				}
			};
			a.put("c", "d");
			return a;
		}

		public class Something {
			public void put(Object o, Object o2) {
			}
		}
	}
}
