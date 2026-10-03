package jadx.tests.integration.enums

object TestEnumsWithAssertFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.enums;

public class TestEnumsWithAssertFixture {

	public static class TestCls {
		public enum Numbers {
			ONE(1), TWO(2), THREE(3);

			private final int num;

			Numbers(int n) {
				this.num = n;
			}

			public int getNum() {
				assert num > 0;
				return num;
			}
		}
	}
}
"""
}
