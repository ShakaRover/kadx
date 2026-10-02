package jadx.tests.integration.generics;

import java.util.List;

public class TestGenerics3Fixture {

	public static class TestCls {

		public static void mthExtendsArray(List<? extends byte[]> list) {
		}

		public static void mthSuperArray(List<? super int[]> list) {
		}

		public static void mthSuperInteger(List<? super Integer> list) {
		}

		public static void mthExtendsString(List<? super String> list) {
		}
	}
}
