package kadx.tests.integration.trycatch

object TestTryCatchFinally9Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class TestTryCatchFinally9Fixture {

	public static class TestCls {
		public String test() throws IOException {
			InputStream input = null;
			try {
				input = this.getClass().getResourceAsStream("resource");
				Scanner scanner = new Scanner(input).useDelimiter("\\A");
				return scanner.hasNext() ? scanner.next() : "";
			} finally {
				if (input != null) {
					input.close();
				}
			}
		}
	}
}
"""
}
