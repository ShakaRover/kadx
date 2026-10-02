package jadx.tests.integration.others;

public class TestReturnWrappingFixture {
	public static class TestCls {

		public static int f1(int arg0) {
			switch (arg0) {
				case 1:
					return 255;
			}
			return arg0 + 1;
		}

		public static Object f2(Object arg0, int arg1) {
			Object ret = null;
			int i = arg1;
			if (arg0 == null) {
				return ret + Integer.toHexString(i);
			}
			i++;
			try {
				ret = new Object().getClass();
			} catch (Exception e) {
				ret = "Qwerty";
			}
			return i > 128 ? arg0.toString() + ret.toString() : i;
		}

		public static int f3(int arg0) {
			while (arg0 > 10) {
				int abc = 951;
				if (arg0 == 255) {
					return arg0 + 2;
				}
				arg0 -= abc;
			}
			return arg0;
		}
	}
}
