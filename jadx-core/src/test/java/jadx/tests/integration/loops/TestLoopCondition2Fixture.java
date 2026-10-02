package jadx.tests.integration.loops;

public class TestLoopCondition2Fixture {

	public static class TestCls {

		public int test(boolean a) {
			int i = 0;
			while (a && i < 10) {
				i++;
			}
			return i;
		}
	}
}
