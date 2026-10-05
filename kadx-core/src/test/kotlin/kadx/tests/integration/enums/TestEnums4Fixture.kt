package kadx.tests.integration.enums

object TestEnums4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.enums;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestEnums4Fixture {

	public static class TestCls {
		public enum ResType {
			CODE(".dex", ".class"),
			MANIFEST("AndroidManifest.xml"),
			XML(".xml"),
			ARSC(".arsc"),
			FONT(".ttf"),
			IMG(".png", ".gif", ".jpg"),
			LIB(".so"),
			UNKNOWN;

			private final String[] exts;

			ResType(String... extensions) {
				this.exts = extensions;
			}

			public String[] getExts() {
				return exts;
			}
		}

		public void check() {
			assertThat(ResType.CODE.getExts()).containsExactly(new String[] { ".dex", ".class" });
		}
	}
}
"""
}
