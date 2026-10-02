package jadx.tests.integration.loops;

import java.util.Set;

public class TestIterableForEach3Fixture {

	public static class TestCls<T extends String> {
		private Set<T> a;
		private Set<T> b;

		public void test(T str) {
			Set<T> set = str.length() == 1 ? a : b;
			for (T s : set) {
				if (s.length() == str.length()) {
					if (str.length() == 0) {
						set.remove(s);
					} else {
						set.add(str);
					}
					return;
				}
			}
		}
	}
}
