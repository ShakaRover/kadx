package jadx.tests.integration.invoke;

public class TestOverloadedMethodInvoke2Fixture {

	public static class AbstractItem {

		public void doSomething(Container c, Item i) {
			c.add(i);
		}

		public static class Container {

			public <T extends AbstractItem> int add(T t) {
				return 0;
			}

			public void add(AbstractItem... item) {
			}
		}

		public static class Item extends AbstractItem {
		}
	}
}
