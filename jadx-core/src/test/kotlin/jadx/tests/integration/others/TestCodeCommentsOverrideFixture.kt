package jadx.tests.integration.others

object TestCodeCommentsOverrideFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestCodeCommentsOverrideFixture {

	public static class TestCls {
		public interface I {
			void mth();
		}

		public static class A implements I {
			@Override
			public void mth() {
				System.out.println("mth");
			}
		}
	}
}
"""
}
