package jadx.tests.integration.loops;

import java.util.Iterator;

public class TestDoWhileBreak3Fixture {

	public static class TestCls {
		Iterator<String> it;

		public void test() {
			do {
				if (!it.hasNext()) {
					break;
				}
			} while (it.next() != null);
		}
	}
}
