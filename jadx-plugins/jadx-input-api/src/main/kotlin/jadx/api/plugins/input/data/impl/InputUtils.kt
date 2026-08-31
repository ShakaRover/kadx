package jadx.api.plugins.input.data.impl

/**
 * 输入模块内部工具类。
 */
public class InputUtils {

	public companion object {
		/**
		 * 把指令偏移格式化为 4 位十六进制字符串（如 0x0018）。
		 * @param offset 指令偏移
		 */
		@JvmStatic
		public fun formatOffset(offset: Int): String = String.format("0x%04x", offset)
	}
}
