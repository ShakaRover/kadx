package kadx.tests.integration.trycatch

object TestMultiExceptionCatchFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import java.security.ProviderException;
import java.time.DateTimeException;

public class TestMultiExceptionCatchFixture {

	public static class TestCls {
		public void test() {
			try {
				System.out.println("Test");
			} catch (ProviderException | DateTimeException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
"""
}
