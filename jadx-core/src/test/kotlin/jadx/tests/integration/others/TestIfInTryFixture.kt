package jadx.tests.integration.others

object TestIfInTryFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import java.io.File;
import java.io.IOException;

public class TestIfInTryFixture {

	public static class TestCls {
		public File dir;

		public int test() {
			try {
				int a = f();
				if (a != 0) {
					return a;
				}
			} catch (Exception e) {
				// skip
			}
			try {
				f();
				return 1;
			} catch (IOException e) {
				return -1;
			}
		}

		private int f() throws IOException {
			return 0;
		}
	}
}
"""
}
