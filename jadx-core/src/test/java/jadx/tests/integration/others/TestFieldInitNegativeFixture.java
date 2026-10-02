package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestFieldInitNegativeFixture {

	public static class TestCls {
		StringBuilder sb;
		int field;

		public TestCls() {
			initBuilder(new StringBuilder("sb"));
			this.field = initField();
			this.sb.append(this.field);
		}

		private void initBuilder(StringBuilder sb) {
			this.sb = sb;
		}

		private int initField() {
			return sb.length();
		}

		public String getStr() {
			return sb.toString();
		}

		public void check() {
			assertThat(new TestCls().getStr()).isEqualTo("sb2"); // no NPE
		}
	}
}
