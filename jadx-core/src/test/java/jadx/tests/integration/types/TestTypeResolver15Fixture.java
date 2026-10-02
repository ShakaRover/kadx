package jadx.tests.integration.types;

public class TestTypeResolver15Fixture {

	public static class TestCls {
		private void test(boolean z) {
			useInt(z ? 0 : 8);
			useInt(!z ? 1 : 0); // replaced with xor in smali test
		}

		private void useInt(int i) {
		}
	}
}
