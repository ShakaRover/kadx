package kadx.tests.integration.types

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 资源查询的 `Cursor` 类型推断：`query` 变量不应被推断成带 `?` 的未解析类型（不出现 `? r2`）。
 */
class TestTypeResolver14 : SmaliTest() {
	// @formatter:off
	/*
		public Date test() throws Exception {
			Date date = null;
			Long l = null;
			Cursor query = DBUtil.query(false, (CancellationSignal) null);
			try {
				if (query.moveToFirst()) {
					if (!query.isNull(0)) {
						l = Long.valueOf(query.getLong(0));
					}
					date = this.this$0.toDate(l);
				}
				return date;
			} finally {
				query.close();
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmali())
			.code()
			.doesNotContain("? r2")
	}
}
