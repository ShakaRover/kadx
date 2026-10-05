package kadx.tests.integration.android

object TestResConstReplace2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.android;

public class TestResConstReplace2Fixture {

	public static class TestCls {
		public int test(int i) {
			switch (i) {
				case 0x0101013f: // android.R.attr.minWidth
					return 1;
				case 0x01010140: // android.R.attr.minHeight
					return 2;
				default:
					return 0;
			}

		}
	}
}
"""
}
