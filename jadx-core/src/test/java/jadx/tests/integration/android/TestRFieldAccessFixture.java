package jadx.tests.integration.android;

@SuppressWarnings("TypeName")
public class TestRFieldAccessFixture {

	public static class R {
		public static final class id {
			public static final int BUTTON_01 = 2131230730;
		}
	}

	public static class TestR {
		public int test() {
			return R.id.BUTTON_01;
		}
	}
}
