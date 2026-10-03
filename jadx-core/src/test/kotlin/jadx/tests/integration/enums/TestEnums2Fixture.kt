package jadx.tests.integration.enums

object TestEnums2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.enums;

public class TestEnums2Fixture {

	public static class TestCls {

		public enum Operation {
			PLUS {
				@Override
				public int apply(int x, int y) {
					return x + y;
				}
			},
			MINUS {
				@Override
				public int apply(int x, int y) {
					return x - y;
				}
			};

			public abstract int apply(int x, int y);
		}
	}
}
"""
}
