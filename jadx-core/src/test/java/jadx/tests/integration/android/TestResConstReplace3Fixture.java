package jadx.tests.integration.android;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class TestResConstReplace3Fixture {

	@Retention(RetentionPolicy.RUNTIME)
	public @interface UsesAndroidResource {
		int value() default 0;
	}

	@UsesAndroidResource(17039370 /* android.R.string.ok */)
	public static class TestCls {
		public void test(@UsesAndroidResource(17039370 /* android.R.string.ok */) int i) {
		}
	}
}
