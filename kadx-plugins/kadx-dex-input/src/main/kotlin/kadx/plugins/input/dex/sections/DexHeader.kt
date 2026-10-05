package kadx.plugins.input.dex.sections

import kadx.plugins.input.dex.DexException

/**
 * DEX 文件头（header_item）：解析 magic/version、各 section 的偏移与大小，
 * 以及 map_list 中的 call_site / method_handle 扩展区位置。
 *
 * **背景**：
 * 1. [DexReader] 构造时立即解析本类；所有多字节整型均为小端序（由 [SectionReader] 保证）；
 * 2. 部分字段（checksum/signature/fileSize/link_* 等）与原 Java 一致只读取推进游标、不做校验；
 * 3. Kotlin 调用方使用属性语法访问（如 `header.typeIdsOff`），字节码生成的 getter
 *    与原 Java 方法名完全相同，Java 调用方零改动。
 */
public class DexHeader(buf: SectionReader) {

	public val version: String
	public val classDefsSize: Int
	public val classDefsOff: Int
	public val stringIdsOff: Int
	public val typeIdsOff: Int
	public val typeIdsSize: Int
	public val fieldIdsSize: Int
	public val fieldIdsOff: Int
	public val protoIdsSize: Int
	public val protoIdsOff: Int
	public val methodIdsOff: Int
	public val methodIdsSize: Int

	// 扩展区偏移：仅 DEX v3.5+（invoke-custom）才有，默认 0 表示不存在
	public var callSiteOff: Int = 0
	public var methodHandleOff: Int = 0

	init {
		buf.readByteArray(4) // magic "dex\n"
		version = buf.readString(3) // 版本号，如 "035"
		buf.skip(1) // magic+version 的 null 终止符（共 8 字节）
		buf.readInt() // checksum：文件其余部分的 SHA-1（此处不校验）
		buf.readByteArray(20) // signature：SHA-1 摘要（此处不校验）
		buf.readInt() // fileSize
		buf.readInt() // headerSize
		val endianTag = buf.readInt()
		if (endianTag != DexConsts.ENDIAN_CONSTANT) {
			throw DexException("Unexpected endian tag: 0x" + Integer.toHexString(endianTag))
		}
		buf.skip(8) // link_size + link_off（DEX v1+ 不使用）
		val mapListOff = buf.readInt()

		buf.readInt() // string_ids_size（未使用，仅推进游标）
		stringIdsOff = buf.readInt()
		typeIdsSize = buf.readInt()
		typeIdsOff = buf.readInt()
		protoIdsSize = buf.readInt()
		protoIdsOff = buf.readInt()
		fieldIdsSize = buf.readInt()
		fieldIdsOff = buf.readInt()
		methodIdsSize = buf.readInt()
		methodIdsOff = buf.readInt()
		classDefsSize = buf.readInt()
		classDefsOff = buf.readInt()
		buf.skip(8) // data_size + data_off（未使用，仅推进游标）

		readMapList(buf, mapListOff)
	}

	private fun readMapList(buf: SectionReader, mapListOff: Int) {
		buf.absPos(mapListOff)
		val size = buf.readInt()
		for (i in 0 until size) {
			val type = buf.readUShort()
			buf.skip(6) // map_list_item 的 size + unused 字段
			val offset = buf.readInt()

			when (type) {
				0x0007 -> callSiteOff = offset

				// TYPE_CALL_SITE_ID_ITEM
				0x0008 -> methodHandleOff = offset // TYPE_METHOD_HANDLE_ITEM
			}
		}
	}
}
