package jadx.tests.integration.names

object TestConstructorArgNamesFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.names;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestConstructorArgNamesFixture {

	@SuppressWarnings({ "FieldCanBeLocal", "FieldMayBeFinal", "StaticVariableName", "ParameterName" })
	public static class TestCls {
		private static String STR = "static field";
		private final String str;
		private final String store;

		public TestCls(String str, String STR) {
			this.str = str;
			this.store = STR;
		}

		public TestCls() {
			this.str = "a";
			this.store = STR;
		}

		public void check() {
			assertThat(new TestCls("a", "b").store).isEqualTo("b");
			assertThat(new TestCls().store).isEqualTo(STR);
		}
	}
}
"""
}
