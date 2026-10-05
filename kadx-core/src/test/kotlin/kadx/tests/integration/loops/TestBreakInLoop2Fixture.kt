package kadx.tests.integration.loops

object TestBreakInLoop2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import java.util.List;

public class TestBreakInLoop2Fixture {

	@SuppressWarnings({ "BusyWait", "ResultOfMethodCallIgnored" })
	public static class TestCls {
		public void test(List<Integer> data) throws Exception {
			for (;;) {
				try {
					funcB(data);
					break;
				} catch (Exception ex) {
					if (funcC()) {
						throw ex;
					}
					data.clear();
				}
				Thread.sleep(100L);
			}
		}

		private boolean funcB(List<Integer> data) {
			return false;
		}

		private boolean funcC() {
			return true;
		}
	}
}
"""
}
