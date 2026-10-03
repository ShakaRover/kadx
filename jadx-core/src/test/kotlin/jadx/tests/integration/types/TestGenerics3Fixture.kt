package jadx.tests.integration.types

object TestGenerics3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestGenerics3Fixture {

	public static class TestCls {
		public static void test() {
			List<String> classes = getClasses();
			Collections.sort(classes);
			int passed = 0;
			for (String cls : classes) {
				if (runTest(cls)) {
					passed++;
				}
			}
			int failed = classes.size() - passed;
			System.out.println("failed: " + failed);
		}

		private static boolean runTest(String clsName) {
			return false;
		}

		private static List<String> getClasses() {
			return new ArrayList<>();
		}
	}
}
"""
}
