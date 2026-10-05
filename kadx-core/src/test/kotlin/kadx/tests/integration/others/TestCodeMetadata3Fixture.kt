package kadx.tests.integration.others

object TestCodeMetadata3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestCodeMetadata3Fixture {

	public static class TestCls {
		public String test(String str) {
			int k = str.length();
			k++;
			return str + ':' + k;
		}
	}
}
"""
}
