package jadx.tests.integration.rename

object TestFieldWithGenericRenameFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.rename;

import java.util.List;

public class TestFieldWithGenericRenameFixture {

	public static class TestCls {
		List<String> list;
	}
}
"""
}
