package kadx.tests.integration.invoke

object TestInvokeWithWideVarsFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.invoke;

public class TestInvokeWithWideVarsFixture {

	@SuppressWarnings("SameParameterValue")
	public static class TestCls {

		public long test1() {
			return call(1, 2L);
		}

		public long test2() {
			return rangeCall(1L, 2, 3.0d, (byte) 4);
		}

		private long call(int a, long b) {
			return 0L;
		}

		private long rangeCall(long a, int b, double c, byte d) {
			return 0L;
		}
	}
}
"""
}
