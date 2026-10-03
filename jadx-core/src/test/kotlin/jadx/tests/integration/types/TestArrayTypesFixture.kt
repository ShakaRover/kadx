package jadx.tests.integration.types

object TestArrayTypesFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

public class TestArrayTypesFixture {

	@SuppressWarnings({ "ThrowablePrintedToSystemOut", "unused" })
	public static class TestCls {

		public void test() {
			Exception e = new Exception();
			System.out.println(e);
			use(new Object[] { e });
		}

		public void use(Object[] arr) {
		}

		public void check() {
			test();
		}
	}
}
"""
}
