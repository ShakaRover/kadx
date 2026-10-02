package jadx.tests.integration.others;

public class TestPrimitiveCastsFixture {

	public static class TestCls {

		public void test() {
			useShort((short) 0);
			useShort((short) getInt());
			useByte((byte) 0);
			useByte((byte) getInt());
			useChar((char) 0);
			useChar((char) getInt());

			useShort((short) 0L);
			useShort((short) getLong());
			useByte((byte) 0L);
			useByte((byte) getLong());
			useChar((char) 0L);
			useChar((char) getLong());

			useShort((short) ' ');
			useShort((short) getChar());
			useByte((byte) ' ');
			useByte((byte) getChar());

			useInt((byte) 7);
			useInt((char) ' ');
			useInt(getChar());
			useInt((int) 2L);
			useInt((int) getLong());
		}

		private long getLong() {
			return 1L;
		}

		private char getChar() {
			return ' ';
		}

		private int getInt() {
			return 1;
		}

		private void useChar(char c) {
		}

		private void useByte(byte b) {
		}

		private void useShort(short s) {
		}

		private void useInt(int i) {
		}
	}
}
