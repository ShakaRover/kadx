package kadx.tests.integration.loops

object TestArrayForEach3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.loops;

import static org.assertj.core.api.Assertions.fail;

/**
 * Issue #977
 */
public class TestArrayForEach3Fixture {

	public static class TestCls {
		public void test(String[] arr) {
			for (String s : arr) {
				if (s.length() > 0) {
					return;
				}
			}
			throw new IllegalArgumentException("All strings are empty");
		}

		public void check() {
			test(new String[] { "", "a" }); // no exception
			try {
				test(new String[] { "", "" });
				fail("IllegalArgumentException expected");
			} catch (IllegalArgumentException e) {
				// expected
			}
		}
	}
}
"""
}
