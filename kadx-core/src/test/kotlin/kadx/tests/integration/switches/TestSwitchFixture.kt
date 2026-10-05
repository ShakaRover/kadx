package kadx.tests.integration.switches

object TestSwitchFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.switches;

public class TestSwitchFixture {

	public static class TestCls {
		public String test(String str) {
			int len = str.length();
			StringBuilder sb = new StringBuilder(len);
			for (int i = 0; i < len; i++) {
				char c = str.charAt(i);
				switch (c) {
					case '.':
					case '/':
						sb.append('_');
						break;

					case ']':
						sb.append('A');
						break;

					case '?':
						break;

					default:
						sb.append(c);
						break;
				}
			}
			return sb.toString();
		}
	}
}
"""
}
