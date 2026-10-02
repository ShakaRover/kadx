package jadx.tests.integration.types;

public class TestTypeInheritanceFixture {

	public static class TestCls {

		public interface IRoot {
		}

		public interface IBase extends IRoot {
		}

		public static class A implements IBase {
		}

		public static class B implements IBase {
			public void b() {
			}
		}

		public static void test(boolean z) {
			IBase impl;
			if (z) {
				impl = new A();
			} else {
				B b = new B();
				b.b();
				impl = b; // this move is removed in no-debug byte-code
			}
			useBase(impl);
			useRoot(impl);
		}

		private static void useRoot(IRoot root) {
		}

		private static void useBase(IBase base) {
		}
	}
}
