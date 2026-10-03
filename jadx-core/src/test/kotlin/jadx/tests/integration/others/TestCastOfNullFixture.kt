package jadx.tests.integration.others

object TestCastOfNullFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import java.util.List;

@SuppressWarnings("unused")
public class TestCastOfNullFixture {

	public static class TestCls {

		public void test() {
			m((long[]) null);
			m((String) null);
			m((List<String>) null);
		}

		public void m(long[] a) {
		}

		public void m(String s) {
		}

		public void m(List<String> list) {
		}
	}
}
"""
}
