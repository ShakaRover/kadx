package jadx.tests.integration.trycatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TestTryWithResourcesFixture {

	public static class TestCls {

		public static void writeFully(File file, byte[] data) throws IOException {
			try (OutputStream out = new FileOutputStream(file)) {
				out.write(data);
			}
		}
	}
}
