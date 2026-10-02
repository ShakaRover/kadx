package jadx.tests.integration.trycatch;

import java.io.File;

public class TestInlineInCatchFixture {

	public static class TestCls {
		private File dir;

		public int test() {
			File output = null;
			try {
				output = File.createTempFile("f", "a", dir);
				if (!output.exists()) {
					return 1;
				}
				return 0;
			} catch (Exception e) {
				if (output != null) {
					output.delete();
				}
				return 2;
			}
		}
	}
}
