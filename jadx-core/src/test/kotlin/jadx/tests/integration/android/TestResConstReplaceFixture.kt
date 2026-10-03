package jadx.tests.integration.android

object TestResConstReplaceFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.android;

public class TestResConstReplaceFixture {

	public static class TestCls {
		public int test() {
			return 0x0101013f; // android.R.attr.minWidth
		}
	}
}
"""
}
