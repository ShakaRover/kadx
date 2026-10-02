package jadx.tests.integration.enums;

import static org.assertj.core.api.Assertions.assertThat;

public class TestInnerEnumsFixture {

	public static class TestCls {

		public enum Numbers {
			ONE((byte) 1, NumString.ONE), TWO((byte) 2, NumString.TWO);

			private final byte num;
			private final NumString str;

			public enum NumString {
				ONE("one"), TWO("two");

				private final String name;

				NumString(String name) {
					this.name = name;
				}

				public String getName() {
					return name;
				}
			}

			Numbers(byte n, NumString str) {
				this.num = n;
				this.str = str;
			}

			public int getNum() {
				return num;
			}

			public NumString getNumStr() {
				return str;
			}

			public String getName() {
				return str.getName();
			}
		}

		public void check() {
			assertThat(Numbers.ONE.getNum()).isEqualTo(1);
			assertThat(Numbers.ONE.getNumStr()).isEqualTo(Numbers.NumString.ONE);
			assertThat(Numbers.ONE.getName()).isEqualTo("one");
		}
	}
}
