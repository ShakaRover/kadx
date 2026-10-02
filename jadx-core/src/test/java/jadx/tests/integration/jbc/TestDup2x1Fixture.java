package jadx.tests.integration.jbc;

public class TestDup2x1Fixture {

	@SuppressWarnings({ "FieldCanBeLocal", "checkstyle:InnerAssignment", "unused" })
	public static class TestCls {
		private long value;

		public long setValue(long v) {
			return this.value = v;
		}
	}
}
