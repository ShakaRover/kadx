package jadx.tests.integration.trycatch;

import java.io.IOException;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

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
