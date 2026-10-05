package kadx.tests.integration.types

object TestPrimitivesInIfFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

import static org.assertj.core.api.Assertions.assertThat;

public class TestPrimitivesInIfFixture {

	public static class TestCls {

		public boolean test(String str) {
			short sh = Short.parseShort(str);
			int i = Integer.parseInt(str);
			System.out.println(sh + " vs " + i);
			return sh == i;
		}

		public void check() {
			assertThat(test("1")).isTrue();
		}
	}
}
"""
}
