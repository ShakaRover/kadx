package jadx.plugins.input.dex

import jadx.api.plugins.input.data.IClassData
import jadx.plugins.input.dex.sections.DexClassData
import jadx.plugins.input.dex.sections.DexHeader
import jadx.plugins.input.dex.sections.SectionReader
import jadx.plugins.input.dex.sections.annotations.AnnotationsParser
import java.nio.ByteBuffer
import java.util.function.Consumer

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
	content: ByteArray,
	offset: Int,
) {

	val buf: ByteBuffer
	val header: DexHeader

	init {
		buf = ByteBuffer.wrap(content)
		header = DexHeader(SectionReader(this, offset))
	}

	/**
	 * 遍历本 DEX 文件中的全部 class，逐个回调 [consumer]。
	 */
	public fun visitClasses(consumer: Consumer<IClassData>) {
		val count = header.classDefsSize
		if (count == 0) {
			return
		}
		val classDefsOff = header.classDefsOff
		val inReader = SectionReader(this, classDefsOff)
		val annotationsParser = AnnotationsParser(inReader.copy(), inReader.copy())
		val classData = DexClassData(inReader, annotationsParser)
		for (i in 0 until count) {
			consumer.accept(classData)
			inReader.shiftOffset(DexClassData.SIZE)
		}
	}

	override fun toString(): String = inputFileName
}
