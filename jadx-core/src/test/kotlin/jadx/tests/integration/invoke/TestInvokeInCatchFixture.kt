package jadx.tests.integration.invoke

object TestInvokeInCatchFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.invoke;

import java.io.IOException;

public class TestInvokeInCatchFixture {

	public static class TestCls {
		private static final String TAG = "TAG";

		public void test(int[] a, int b) {
			try {
				exc();
			} catch (IOException e) {
				if (b == 1) {
					log(TAG, "Error: {}", e.getMessage());
				}
			}
		}

		private static void log(String tag, String str, String... args) {
		}

		private void exc() throws IOException {
			throw new IOException();
		}
	}
}
"""
}
