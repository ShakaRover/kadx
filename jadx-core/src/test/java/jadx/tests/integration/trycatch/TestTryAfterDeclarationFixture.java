package jadx.tests.integration.trycatch;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TestTryAfterDeclarationFixture {

	static class TestClass {
		public static void consume() throws IOException {
			InputStream bis = null;
			try {
				bis = new FileInputStream("1.txt");
				while (bis != null) {
					System.out.println("c");
				}
			} catch (final IOException ignore) {
			}
		}
	}
}
