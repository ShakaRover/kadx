package jadx.tests.integration.conditions;

public class TestIfCodeStyleFixture {

	@SuppressWarnings({ "ConstantConditions", "FieldCanBeLocal", "unused" })
	public static class TestCls {

		private String moduleName;
		private String modulePath;
		private String preinstalledModulePath;
		private long versionCode;
		private String versionName;
		private boolean isFactory;
		private boolean isActive;

		public void test(Parcel parcel) {
			int startPos = parcel.dataPosition();
			int size = parcel.readInt();
			if (size < 0) {
				if (startPos > Integer.MAX_VALUE - size) {
					throw new RuntimeException("Overflow in the size of parcelable");
				}
				parcel.setDataPosition(startPos + size);
				return;
			}
			try {
				if (parcel.dataPosition() - startPos >= size) {
					if (startPos > Integer.MAX_VALUE - size) {
						throw new RuntimeException("Overflow in the size of parcelable");
					}
					parcel.setDataPosition(startPos + size);
					return;
				}
				this.moduleName = parcel.readString();
				if (parcel.dataPosition() - startPos >= size) {
					if (startPos > Integer.MAX_VALUE - size) {
						throw new RuntimeException("Overflow in the size of parcelable");
					}
					parcel.setDataPosition(startPos + size);
					return;
				}
				this.modulePath = parcel.readString();
				if (parcel.dataPosition() - startPos >= size) {
					if (startPos > Integer.MAX_VALUE - size) {
						throw new RuntimeException("Overflow in the size of parcelable");
					}
					parcel.setDataPosition(startPos + size);
					return;
				}
				this.preinstalledModulePath = parcel.readString();
				if (parcel.dataPosition() - startPos >= size) {
					if (startPos > Integer.MAX_VALUE - size) {
						throw new RuntimeException("Overflow in the size of parcelable");
					}
					parcel.setDataPosition(startPos + size);
					return;
				}
				this.versionCode = parcel.readLong();
				if (parcel.dataPosition() - startPos >= size) {
					if (startPos > Integer.MAX_VALUE - size) {
						throw new RuntimeException("Overflow in the size of parcelable");
					}
					parcel.setDataPosition(startPos + size);
					return;
				}
				this.versionName = parcel.readString();
				if (parcel.dataPosition() - startPos >= size) {
					if (startPos > Integer.MAX_VALUE - size) {
						throw new RuntimeException("Overflow in the size of parcelable");
					}
					parcel.setDataPosition(startPos + size);
					return;
				}
				this.isFactory = parcel.readInt() != 0;
				if (parcel.dataPosition() - startPos >= size) {
					if (startPos > Integer.MAX_VALUE - size) {
						throw new RuntimeException("Overflow in the size of parcelable");
					}
					parcel.setDataPosition(startPos + size);
					return;
				}
				this.isActive = parcel.readInt() != 0;
				if (startPos > Integer.MAX_VALUE - size) {
					throw new RuntimeException("Overflow in the size of parcelable");
				}
				parcel.setDataPosition(startPos + size);
			} catch (Throwable e) {
				if (startPos <= Integer.MAX_VALUE - size) {
					parcel.setDataPosition(startPos + size);
					throw e;
				}
				throw new RuntimeException("Overflow in the size of parcelable");
			}
		}

		private static class Parcel {
			public void setDataPosition(int i) {
			}

			public int dataPosition() {
				return 0;
			}

			public int readInt() {
				return 0;
			}

			public String readString() {
				return null;
			}

			public long readLong() {
				return 0;
			}
		}
	}
}
