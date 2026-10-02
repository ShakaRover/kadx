package jadx.tests.integration.loops;

import java.util.Iterator;

public class TestLoopDetection4Fixture {

	public static class TestCls {
		private Iterator<String> iterator;
		private SomeCls filter;

		public String test() {
			while (iterator.hasNext()) {
				String next = iterator.next();
				String filtered = filter.filter(next);
				if (filtered != null) {
					return filtered;
				}
			}
			return null;
		}

		private class SomeCls {
			public String filter(String str) {
				return str;
			}
		}
	}
}
