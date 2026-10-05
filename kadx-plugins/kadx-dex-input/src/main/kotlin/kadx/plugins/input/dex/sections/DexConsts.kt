package kadx.plugins.input.dex.sections

/**
 * DEX 文件格式常量定义。
 *
 * **背景**：集中存放 DEX 文件头魔数、字节序校验常数与索引哨兵值，
 * 供 [DexFileLoader]（魔数识别）、[SectionReader]（NO_INDEX 判空）等解析组件共享。
 */
public class DexConsts {

	public companion object {
		/** DEX 文件头魔数：`dex\n`（0x64 0x65 0x78 0x0a）*/
		public val DEX_FILE_MAGIC: ByteArray = byteArrayOf(0x64, 0x65, 0x78, 0x0a)

		/** ZIP 文件头魔数：`PK\x03\x04`（.apk/.zip 容器识别）*/
		public val ZIP_FILE_MAGIC: ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

		/** 魔数最大长度（DEX 与 ZIP 均为 4 字节）*/
		public const val MAX_MAGIC_SIZE: Int = 4

		/** DEX header 中的 endian_tag，固定为 0x12345678（小端序校验值）*/
		public const val ENDIAN_CONSTANT: Int = 0x12345678

		/** 无效索引哨兵：DEX 中用 -1 表示"无此引用" */
		public const val NO_INDEX: Int = -1
	}
}
