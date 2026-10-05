package kadx.tests.integration.inline

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * smali 用例：局部变量 `Bundle arguments` 的声明应与其赋值合并，且参数注解保留。
 */
class TestInline7 : SmaliTest() {
	// @formatter:off
	/*
		public void onViewCreated(View view, @Nullable Bundle bundle) {
			super.onViewCreated(view, bundle);
			view.findViewById(R.id.done_button_early_release_failure).setOnClickListener(new SafeClickListener(this));
			Bundle arguments = getArguments();
			if (arguments != null) {
				((TextView) view.findViewById(R.id.summary_content_early_release_failure)).setText(
					getString(R.string.withdraw_id_capture_failure_content,
						new Object[]{arguments.getString("withdrawAmount"), arguments.getString ("withdrawHoldTime")})
				);
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		disableCompilation()
		assertThat(getClassNodeFromSmaliWithPkg("inline", "TestInline7"))
			.code()
			.doesNotContain("Bundle arguments;")
			.containsOne("Bundle arguments = getArguments();")
			.containsOne("@Nullable Bundle bundle")
	}
}
