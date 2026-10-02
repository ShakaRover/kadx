package jadx.tests.integration.enums;

public class TestEnumsFixture {

	public static class TestCls {

		public enum EmptyEnum {
		}

		@SuppressWarnings("NoWhitespaceBefore")
		public enum EmptyEnum2 {
			;

			public static void mth() {
			}
		}

		public enum Direction {
			NORTH,
			SOUTH,
			EAST,
			WEST
		}

		public enum Singleton {
			INSTANCE;

			public String test() {
				return "";
			}
		}
	}
}
