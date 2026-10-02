package jadx.tests.integration.loops;

public class TestLoopConditionInvokeFixture {

	public static class TestCls {
		private static final char STOP_CHAR = 0;
		private int pos;

		public boolean test(char lastChar) {
			int startPos = pos;
			char ch;
			while ((ch = next()) != STOP_CHAR) {
				if (ch == lastChar) {
					return true;
				}
			}
			pos = startPos;
			return false;
		}

		private char next() {
			return 0;
		}
	}
}
