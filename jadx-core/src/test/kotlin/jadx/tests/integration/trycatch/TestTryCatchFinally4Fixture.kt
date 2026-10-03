package jadx.tests.integration.trycatch

object TestTryCatchFinally4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.trycatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TestTryCatchFinally4Fixture {

	public static class TestCls {
		public void test() throws IOException {
			File file = File.createTempFile("test", "txt");
			OutputStream outputStream = new FileOutputStream(file);
			try {
				outputStream.write(1);
			} finally {
				try {
					outputStream.close();
					file.delete();
				} catch (IOException ignored) {
				}
			}
		}
	}
}
"""
}
