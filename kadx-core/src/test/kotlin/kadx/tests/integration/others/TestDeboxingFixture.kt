package kadx.tests.integration.others

object TestDeboxingFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestDeboxingFixture {

	public static class TestCls {
		public Object testInt() {
			return 1;
		}

		public Object testBoolean() {
			return true;
		}

		public Object testByte() {
			return (byte) 2;
		}

		public Short testShort() {
			return 3;
		}

		public Character testChar() {
			return 'c';
		}

		public Long testLong() {
			return 4L;
		}

		public void testConstInline() {
			Boolean v = true;
			use(v);
			use(v);
		}

		private void use(Boolean v) {
		}

		public void check() {
			// don't mind weird comparisons
			// need to get primitive without using boxing or literal
			// otherwise will get same result after decompilation
			assertThat(testInt()).isEqualTo(Integer.sum(0, 1));
			assertThat(testBoolean()).isEqualTo(Boolean.TRUE);
			assertThat(testByte()).isEqualTo(Byte.parseByte("2"));
			assertThat(testShort()).isEqualTo(Short.parseShort("3"));
			assertThat(testChar()).isEqualTo("c".charAt(0));
			assertThat(testLong()).isEqualTo(Long.valueOf("4"));
			testConstInline();
		}
	}
}
"""
}
