package kadx.plugins.input.dex

import kadx.api.plugins.input.data.IClassData
import kadx.plugins.input.dex.sections.DexClassData
import kadx.plugins.input.dex.sections.DexHeader
import kadx.plugins.input.dex.sections.SectionReader
import kadx.plugins.input.dex.sections.annotations.AnnotationsParser
import java.nio.ByteBuffer

/**
 * 单个已解析的 DEX 文件：持有完整字节数组与解析出的 header，作为各 section reader 的数据源。
 *
 * **背景**：
 * 1. 构造时立即解析 [DexHeader]（其内部 [SectionReader] 依赖 [buf]，因此 [buf] 必须先于 [header] 赋值）；
 * 2. [visitClasses] 遍历 class_defs section：所有 class 共享同一个 [DexClassData] 实例（首次访问时惰性解析），
 *    每轮迭代 reader 游标推进 [DexClassData.SIZE] 字节。
 */
public class DexReader(
	val uniqId: Int,
	val inputFileName: String,
	val buf: ByteBuffer,
	offset: Int,
) {

	/**
	 * 兼容入口：堆内字节数组（默认路径）。
	 *
	 * mmap 路径（S3-A）走主构造器直接传入 `MappedByteBuffer`，
	 * 使 dex 字节不再占用堆内存，且成为可回收的 file-backed clean page。
	 */
	public constructor(uniqId: Int, inputFileName: String, content: ByteArray, offset: Int) :
		this(uniqId, inputFileName, ByteBuffer.wrap(content), offset)

	val header: DexHeader = DexHeader(SectionReader(this, offset))

	/**
	 * 遍历本 DEX 文件中的全部 class，逐个回调 [consumer]。
	 */
	public fun visitClasses(consumer: (IClassData) -> Unit) {
		val count = header.classDefsSize
		if (count == 0) {
			return
		}
		val classDefsOff = header.classDefsOff
		val inReader = SectionReader(this, classDefsOff)
		val annotationsParser = AnnotationsParser(inReader.copy(), inReader.copy())
		val classData = DexClassData(inReader, annotationsParser)
		for (i in 0 until count) {
			consumer(classData)
			inReader.shiftOffset(DexClassData.SIZE)
		}
	}

	override fun toString(): String = inputFileName
}
