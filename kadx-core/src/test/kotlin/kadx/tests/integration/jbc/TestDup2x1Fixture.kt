package kadx.tests.integration.jbc

object TestDup2x1Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.jbc;

public class TestDup2x1Fixture {

	@SuppressWarnings({ "FieldCanBeLocal", "checkstyle:InnerAssignment", "unused" })
	public static class TestCls {
		private long value;

		public long setValue(long v) {
			return this.value = v;
		}
	}
}
"""
}
