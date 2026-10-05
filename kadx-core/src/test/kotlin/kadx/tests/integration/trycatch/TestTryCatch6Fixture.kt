package kadx.tests.integration.trycatch

object TestTryCatch6Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import java.io.IOException;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestTryCatch6Fixture {

	public static class TestCls {
		private static boolean test(Object obj) {
			boolean res = false;
			while (true) {
				try {
					res = exc(obj);
					return res;
				} catch (IOException e) {
					res = true;
				} catch (Throwable e) {
					if (obj == null) {
						obj = new Object();
					}
				}
			}
		}

		private static boolean exc(Object obj) throws IOException {
			if (obj == null) {
				throw new IOException();
			}
			return true;
		}

		public void check() {
			assertThat(test(new Object())).isTrue();
		}
	}
}
"""
}
