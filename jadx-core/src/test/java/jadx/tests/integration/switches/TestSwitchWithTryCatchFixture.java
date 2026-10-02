package jadx.tests.integration.switches;

@SuppressWarnings("checkstyle:printstacktrace")
public class TestSwitchWithTryCatchFixture {

	public static class TestCls {
		void test(int a) {
			switch (a) {
				case 0:
					try {
						exc();
						return;
					} catch (Exception e) {
						e.printStackTrace();
						return;
					}
					// no break;

				case 1:
					try {
						exc();
						return;
					} catch (Exception e) {
						e.printStackTrace();
					}
					break;

				case 2:
					try {
						exc();
					} catch (Exception e) {
						e.printStackTrace();
						return;
					}
					break;

				case 3:
					try {
						exc();
					} catch (Exception e) {
						e.printStackTrace();
					}
					break;
			}
			if (a == 10) {
				System.out.println(a);
			}
		}

		private void exc() throws Exception {
		}
	}
}
