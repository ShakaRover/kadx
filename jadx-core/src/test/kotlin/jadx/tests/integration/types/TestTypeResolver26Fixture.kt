package jadx.tests.integration.types

object TestTypeResolver26Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.types;

import java.util.ArrayList;

public class TestTypeResolver26Fixture {

	@SuppressWarnings({ "rawtypes", "unchecked", "checkstyle:IllegalType" })
	public static class TestCls {
		final ArrayList<String> target = new ArrayList<>();
		final ArrayList source = new ArrayList();

		public void test() {
			((ArrayList) target).add(source.get(0)); // cast removed in bytecode
		}
	}
}
"""
}
