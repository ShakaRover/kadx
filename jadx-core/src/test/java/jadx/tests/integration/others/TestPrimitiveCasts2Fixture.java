package jadx.tests.integration.others;

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
