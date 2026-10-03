package jadx.tests.integration.arith

object TestNumbersFormatFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arith;

public class TestNumbersFormatFixture {

	@SuppressWarnings({ "FieldCanBeLocal", "UnusedAssignment", "unused" })
	public static class TestCls {
		private Object obj;

		public void test() {
			obj = new byte[] { 0, -1, -0xA, (byte) 0xff, Byte.MIN_VALUE, Byte.MAX_VALUE };
			obj = new short[] { 0, -1, -0xA, (short) 0xffff, Short.MIN_VALUE, Short.MAX_VALUE };
			obj = new int[] { 0, -1, -0xA, 0xffff_ffff, Integer.MIN_VALUE, Integer.MAX_VALUE };
			obj = new long[] { 0, -1, -0xA, 0xffff_ffff_ffff_ffffL, Long.MIN_VALUE, Long.MAX_VALUE };
		}
	}
}
"""
}
