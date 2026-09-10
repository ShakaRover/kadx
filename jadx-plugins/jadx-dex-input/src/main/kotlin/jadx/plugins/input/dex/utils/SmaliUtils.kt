package jadx.plugins.input.dex.utils

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 借助 baksmali/dexlib2 生成类级 smali 代码的工具。
 *
 * **背景**：[jadx.plugins.input.dex.sections.DexClassData] 的 `getSmaliCode` 用它把
 * DEX 中的单个类定义反汇编成 smali 文本，供插件 API 消费（如对比/展示原始指令）。
 */
public class SmaliUtils {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SmaliUtils::class.java)

		/**
		 * 把 DEX 中指定偏移处的类定义反汇编为 smali 文本。
		 * @param dexBuf 完整 DEX 字节内容
		 * @param clsDefOffset class_def 条目在 DEX 中的绝对偏移
		 * @return smali 文本；baksmali 失败时返回错误描述（不抛异常）
		 */
		@JvmStatic
		public fun getSmaliCode(dexBuf: ByteArray, clsDefOffset: Int): String {
			val stringWriter = StringWriter()
			try {
				val dexFile = DexBackedDexFile(null, dexBuf)
				val dexBackedClassDef = DexBackedClassDef(dexFile, clsDefOffset, 0)
				val classDefinition = ClassDefinition(BaksmaliOptions(), dexBackedClassDef)
				classDefinition.writeTo(BaksmaliWriter(stringWriter))
			} catch (e: Exception) {
				LOG.error("Error generating smali", e)
				stringWriter.append("Error generating smali code: ")
				stringWriter.append(e.message)
				stringWriter.append(System.lineSeparator())
				e.printStackTrace(PrintWriter(stringWriter, true))
			}
			return stringWriter.toString()
		}
	}
}
