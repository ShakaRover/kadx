package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * finally 提取的负面用例（issue 1592）：不同寄存器不应被错误合并为同一个。
 */
class TestTryCatchFinally15 : SmaliTest() {

	// @formatter:off
	/*
		protected final Parcel test(int i, Parcel parcel) throws RemoteException {
			Parcel obtain = Parcel.obtain();
			try {
				try {
					this.zza.transact(i, parcel, obtain, 0);
					obtain.readException();
					return obtain;
				} catch (RuntimeException e) {
					obtain.recycle();
					throw e;
				}
			} finally {
				parcel.recycle();
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("parcel = Parcel.obtain();")
			.containsOne("this.zza.transact(i, parcel, parcelObtain, 0);")
	}
}
