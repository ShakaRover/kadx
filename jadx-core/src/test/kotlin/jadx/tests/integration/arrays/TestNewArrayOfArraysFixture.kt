package jadx.tests.integration.arrays

object TestNewArrayOfArraysFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.arrays;

public class TestNewArrayOfArraysFixture {

	public static class TestCls {
		public static long[][] anewarrayOfArray(int n) {
			return new long[n][]; // anewarray [J
		}

		public static String[][] anewarrayOfObjectArray(int n) {
			return new String[n][]; // anewarray [Ljava/lang/String;
		}

		public static int[][][] anewarrayDeep(int n) {
			return new int[n][][]; // anewarray [[I
		}

		public static int[][] multiAnewarray(int a, int b) {
			return new int[a][b]; // multianewarray [[I
		}
	}
}
"""
}
