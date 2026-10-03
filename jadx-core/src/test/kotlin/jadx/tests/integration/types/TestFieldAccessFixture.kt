package jadx.tests.integration.types

object TestFieldAccessFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

public class TestFieldAccessFixture {

	public static class TestCls {
		private String field;

		static <T extends TestCls> T testPut(T t) {
			((TestCls) t).field = "";
			return t;
		}

		static <T extends TestCls> T testGet(T t) {
			System.out.println(((TestCls) t).field);
			return t;
		}
	}
}
"""
}
