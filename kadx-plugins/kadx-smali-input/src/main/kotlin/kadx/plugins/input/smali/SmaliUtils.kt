package kadx.plugins.input.smali

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.smali.SmaliOptions
import com.android.tools.smali.smali.smaliLexer
import com.android.tools.smali.smali.smaliParser
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

/**
 * 把 smali 文件汇编成 dex 字节数组的工具。
 *
 * **背景**：ksmali（smali 的 fork）从 4.x 起把前端重写成单趟 ANTLR4 语法 —— 原来的
 * smaliFlexLexer + smaliParser + smaliTreeWalker 三段式被合并，语义动作直接写在 parser 规则里
 * （`smaliTreeWalker` 已不存在）。这里因此直接调用 [smaliLexer] / [smaliParser]，把
 * [DexBuilder] 交给 parser，在内存中完成 smali → dex 转换，不落盘。
 *
 * **错误收集**：ANTLR4 默认把语法错误打到 stderr；这里换掉默认的 error listener，
 * 把消息收进 StringBuilder，出错时统一抛出带完整错误信息的 RuntimeException。
 */
public object SmaliUtils {

	public fun assemble(smaliFile: File, options: SmaliOptions): ByteArray {
		val errors = StringBuilder()
		val errorListener = object : BaseErrorListener() {
			override fun syntaxError(
				recognizer: Recognizer<*, *>?,
				offendingSymbol: Any?,
				line: Int,
				charPositionInLine: Int,
				msg: String?,
				e: RecognitionException?,
			) {
				errors
					.append('\n')
					.append("line ")
					.append(line)
					.append(':')
					.append(charPositionInLine)
					.append(' ')
					.append(msg)
			}
		}
		FileInputStream(smaliFile).use { fis ->
			val lexer = smaliLexer(CharStreams.fromStream(fis, StandardCharsets.UTF_8)).apply {
				setApiLevel(options.apiLevel)
				setSourceFile(smaliFile)
				removeErrorListeners()
				addErrorListener(errorListener)
			}
			val tokens = CommonTokenStream(lexer)
			val dexBuilder = DexBuilder(Opcodes.forApi(options.apiLevel))
			val parser = smaliParser(tokens).apply {
				setBuildParseTree(false)
				setVerboseErrors(options.verboseErrors)
				setAllowOdex(options.allowOdexOpcodes)
				setApiLevel(options.apiLevel)
				setDexBuilder(dexBuilder)
				removeErrorListeners()
				addErrorListener(errorListener)
			}
			parser.smali_file()
			if (parser.numberOfSyntaxErrors > 0 || lexer.numberOfSyntaxErrors > 0) {
				throw RuntimeException("Smali parse error: $errors")
			}
			val dataStore = MemoryDataStore()
			dexBuilder.writeTo(dataStore)
			return dataStore.data
		}
	}
}
