package jadx.tests.integration.enums;

public class TestEnumsWithConstsFixture {

	public static class TestCls {

		public static final int C1 = 1;
		public static final int C2 = 2;
		public static final int C4 = 4;

		public static final String S = "NORTH";

		public enum Direction {
			NORTH,
			SOUTH,
			EAST,
			WEST
		}
	}
}
