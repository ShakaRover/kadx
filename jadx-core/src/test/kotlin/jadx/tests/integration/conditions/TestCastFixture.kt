package jadx.tests.integration.conditions

object TestCastFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestCastFixture {

	public static class TestCls {

		byte myByte;
		short myShort;

		public void test1(boolean a) {
			write(a ? (byte) 0 : 1);
		}

		public void test2(boolean a) {
			write(a ? 0 : myByte);
		}

		public void test3(boolean a) {
			write(a ? 0 : (byte) 127);
		}

		public void test4(boolean a) {
			write(a ? (short) 0 : 1);
		}

		public void test5(boolean a) {
			write(a ? myShort : 0);
		}

		public void test6(boolean a) {
			write(a ? Short.MIN_VALUE : 0);
		}

		public void write(byte b) {
		}

		public void write(short b) {
		}
	}
}
"""
}
