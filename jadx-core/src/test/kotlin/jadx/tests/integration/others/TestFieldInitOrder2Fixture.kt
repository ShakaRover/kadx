package jadx.tests.integration.others

object TestFieldInitOrder2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

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
