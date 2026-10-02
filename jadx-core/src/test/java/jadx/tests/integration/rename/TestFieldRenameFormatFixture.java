package jadx.tests.integration.rename;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class TestFieldRenameFormatFixture {

	@SuppressWarnings({ "unused", "NonSerializableClassWithSerialVersionUID" })
	public static class TestCls {
		private static final long serialVersionUID = -2619335455376089892L;
		@SerializedName("id")
		private int b;
		@SerializedName("title")
		private String c;
		@SerializedName("images")
		private List<String> d;
		@SerializedName("authors")
		private List<String> e;
		@SerializedName("description")
		private String f;
	}
}
