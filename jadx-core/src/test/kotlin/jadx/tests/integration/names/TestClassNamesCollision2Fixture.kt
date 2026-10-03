package jadx.tests.integration.names

object TestClassNamesCollision2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.names;

public class TestClassNamesCollision2Fixture {

	@SuppressWarnings("rawtypes")
	public static class TestCls {
		static class List {
			public static List getList() {
				return null;
			}
		}

		protected List list = List.getList();

		protected void clearList(java.util.List l) {
			l.clear();
		}
	}
}
"""
}
