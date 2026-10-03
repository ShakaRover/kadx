package jadx.gui.ui.codearea

import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenImpl
import org.fife.ui.rsyntaxtextarea.TokenMakerBase
import org.fife.ui.rsyntaxtextarea.TokenTypes
import javax.swing.text.Segment

/**
 * 极简的 [TokenMakerBase] 实现：整行只产生一个 token，不做任何语法解析。
 *
 * **为什么需要它**：纯文本（例如某些资源文件）用默认的 `PlainTextTokenMaker`
 * 解析时可能抛错；用本类可以保证永不解析失败。
 *
 * **注册方式**：在 [AbstractCodeArea] 的静态块里按类名
 * `jadx.gui.ui.codearea.SimpleTokenMaker` 注册，所以保留公开无参构造器。
 */
@Suppress("unused") // 由 AbstractCodeArea 通过类名注册
class SimpleTokenMaker : TokenMakerBase() {
	private val token = TokenImpl()

	init {
		token.setType(TokenTypes.IDENTIFIER)
	}

	override fun getTokenList(segment: Segment, initialTokenType: Int, startOffset: Int): Token {
		// 直接把整个 Segment 当作一个 IDENTIFIER token 返回
		token.text = segment.array
		token.textOffset = startOffset
		token.textCount = segment.count
		token.setOffset(startOffset)
		return token
	}
}
