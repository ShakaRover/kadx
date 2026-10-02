package jadx.tests.integration.enums;

public class TestEnumsInterfaceFixture {

	public static class TestCls {

		public enum Operation implements IOperation {
			PLUS {
				@Override
				public int apply(int x, int y) {
					return x + y;
				}
			},
			MINUS {
				@Override
				public int apply(int x, int y) {
					return x - y;
				}
			}
		}

		public interface IOperation {
			int apply(int x, int y);
		}
	}
}
