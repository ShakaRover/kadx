package kadx.tests.integration.others

object TestFieldInitOrder2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestFieldInitOrder2Fixture {

	@SuppressWarnings({ "SpellCheckingInspection", "StaticVariableName" })
	public static class TestCls {
		static String ZPREFIX = "SOME_";
		private static final String VALUE = ZPREFIX + "VALUE";

		public void check() {
			assertThat(VALUE).isEqualTo("SOME_VALUE");
		}
	}
}
"""
}
