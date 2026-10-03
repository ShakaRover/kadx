package jadx.tests.integration.others

object TestCodeComments2aFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import java.util.Random;

public class TestCodeComments2aFixture {

	@SuppressWarnings("unused")
	public static class TestCls {
		private int f;

		public int test(boolean z) {
			if (z) {
				System.out.println("z");
				return new Random().nextInt();
			}
			return f;
		}
	}
}
"""
}
