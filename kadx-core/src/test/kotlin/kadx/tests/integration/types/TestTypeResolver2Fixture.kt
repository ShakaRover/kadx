package kadx.tests.integration.types

object TestTypeResolver2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

import java.io.IOException;

public class TestTypeResolver2Fixture {

	public static class TestCls {

		public static boolean test(Object obj) throws IOException {
			if (obj != null) {
				return true;
			}
			throw new IOException();
		}
	}
}
"""
}
