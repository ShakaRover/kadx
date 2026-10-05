package kadx.tests.integration.trycatch

object TestTryWithEmptyCatchFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import java.util.Properties;

public class TestTryWithEmptyCatchFixture {

	public static class TestCls extends Exception {
		private static final long serialVersionUID = -5723049816464070603L;
		private Properties field;

		public TestCls(String str) {
			super(str);
			Properties properties = null;
			try {
				if (str.contains("properties")) {
					properties = new Properties();
				}
			} catch (Exception unused) {
				// empty
			}
			this.field = properties;
		}
	}
}
"""
}
