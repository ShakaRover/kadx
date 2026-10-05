package kadx.tests.integration.others

object TestPrimitiveCasts2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestPrimitiveCasts2Fixture {

	@SuppressWarnings("DataFlowIssue")
	public static class TestCls {
		long instanceCount;

		{
			float f = 50.231F;
			instanceCount &= (long) f;
		}
	}
}
"""
}
