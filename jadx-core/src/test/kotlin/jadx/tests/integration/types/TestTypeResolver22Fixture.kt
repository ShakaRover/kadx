package jadx.tests.integration.types

object TestTypeResolver22Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

import java.io.IOException;
import java.io.InputStream;

public class TestTypeResolver22Fixture {

	public static class TestCls {
		public void test(InputStream input, long count) throws IOException {
			long pos = input.skip(count);
			while (pos < count) {
				pos += input.skip(count - pos);
			}
		}
	}
}
"""
}
