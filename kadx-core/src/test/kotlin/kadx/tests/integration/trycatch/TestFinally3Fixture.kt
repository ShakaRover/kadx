package kadx.tests.integration.trycatch

object TestFinally3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class TestFinally3Fixture {

	public static class TestCls {
		public byte[] bytes;

		public byte[] test() throws Exception {
			InputStream inputStream = null;
			try {
				if (bytes == null) {
					if (!validate()) {
						return null;
					}
					inputStream = getInputStream();
					bytes = read(inputStream);
				}
				return convert(bytes);
			} finally {
				close(inputStream);
			}
		}

		private byte[] convert(byte[] bytes) throws Exception {
			return new byte[0];
		}

		private boolean validate() throws Exception {
			return false;
		}

		private InputStream getInputStream() throws Exception {
			return new ByteArrayInputStream(new byte[] {});
		}

		private byte[] read(InputStream in) throws Exception {
			return new byte[] {};
		}

		private static void close(InputStream is) {
		}
	}
}
"""
}
