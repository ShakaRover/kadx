package jadx.gui.ui.codearea

import jadx.api.JavaClass
import jadx.api.JavaMethod
import jadx.api.JavaNode
import jadx.api.plugins.utils.Utils
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenImpl
import org.fife.ui.rsyntaxtextarea.TokenTypes
import org.fife.ui.rsyntaxtextarea.modes.JavaTokenMaker
import org.slf4j.LoggerFactory
import javax.swing.text.Segment

/**
 * jadx 定制的 Java 语法高亮 TokenMaker。
 *
 * **做什么**：先交给父类 [JavaTokenMaker] 完成基础分词，再针对 jadx 反编译输出做三处修正：
 * 1. 把 Java 的“上下文关键字”（如 `var`、`record`）在非关键字位置改回标识符；
 * 2. 修正与 `java.lang.*` 同名的方法/类名，避免被错误着色成函数；
 * 3. 把被拆散的“长类名”（例如 `com.example.Foo`）合并成一个 token。
 *
 * **为什么要修正**：反编译输出常包含与关键字同名的标识符，标准 Java 分词会误判。
 */
class JadxTokenMaker(private val codeArea: CodeArea) : JavaTokenMaker() {

	override fun getTokenList(text: Segment, initialTokenType: Int, startOffset: Int): Token? {
		if (codeArea.isDisposed) {
			return TokenImpl()
		}
		try {
			val tokens = super.getTokenList(text, initialTokenType, startOffset)
			if (tokens != null && tokens.getType() != TokenTypes.NULL) {
				processTokens(tokens)
			}
			return tokens
		} catch (e: Throwable) {
			// JavaTokenMaker 解析失败时会抛出 java.lang.Error
			LOG.error("Process tokens failed for text: {}", text, e)
			return TokenImpl()
		}
	}

	private fun processTokens(tokens: Token) {
		var prev: Token? = null
		var current: Token? = tokens
		while (current != null && current.getType() != TokenTypes.NULL) {
			val cur = current
			val p = prev
			if (p != null) {
				when (cur.getType()) {
					TokenTypes.RESERVED_WORD -> fixContextualKeyword(cur)
					TokenTypes.FUNCTION -> fixIdentifierWithTheSameNameAsJavaLangClass(cur)
					TokenTypes.IDENTIFIER -> current = mergeLongClassNames(p, cur, false)
					TokenTypes.ANNOTATION -> current = mergeLongClassNames(p, cur, true)
				}
			}
			prev = current
			current = current.getNextToken()
		}
	}

	private fun fixContextualKeyword(token: Token) {
		val lexeme = token.getLexeme() // TODO: 每次都会新建字符串，后续可优化
		if (lexeme != null && CONTEXTUAL_KEYWORDS.contains(lexeme)) {
			token.setType(TokenTypes.IDENTIFIER)
		}
	}

	private fun fixIdentifierWithTheSameNameAsJavaLangClass(token: Token) {
		val identifier = codeArea.getJavaNodeAtOffset(token.getTextOffset()) ?: return
		val lexeme = token.getLexeme() ?: return
		if (lexeme == identifier.getName()) {
			token.setType(TokenTypes.IDENTIFIER)
			return
		}
		if (identifier is JavaMethod) {
			val javaCls = identifier.declaringClass
			if (lexeme == javaCls.getName()) {
				token.setType(TokenTypes.IDENTIFIER)
			}
		}
	}

	private fun mergeLongClassNames(prev: Token, current: Token, annotation: Boolean): Token {
		var offset = current.getTextOffset()
		if (annotation) {
			offset++
		}
		val javaCls = codeArea.getJavaClassIfAtPos(offset) ?: return current
		val name = javaCls.getName()
		var lexeme = current.getLexeme() ?: return current
		if (annotation && lexeme.length > 1) {
			lexeme = lexeme.substring(1)
		}
		if (lexeme != name && isClassNameStart(javaCls, lexeme)) {
			// 尝试把长类名合并成一个 token
			val replace = concatTokensUntil(current, name)
			if (replace != null && prev is TokenImpl) {
				val impl = prev as TokenImpl
				impl.setNextToken(replace)
				return replace
			}
		}
		return current
	}

	private fun isClassNameStart(javaNode: JavaNode, lexeme: String): Boolean {
		if (javaNode.getFullName().startsWith(lexeme)) {
			// 完整类名
			return true
		}
		if (javaNode.getTopParentClass()?.getName()?.startsWith(lexeme) == true) {
			// 从父类引用内部类
			return true
		}
		return false
	}

	private fun concatTokensUntil(start: Token, endText: String): TokenImpl? {
		val sb = StringBuilder()
		var current: Token? = start
		while (current != null && current.getType() != TokenTypes.NULL) {
			val text = current.getLexeme()
			if (text != null) {
				sb.append(text)
				if (text == endText) {
					val line = sb.toString().toCharArray()
					val token = TokenImpl(
						line,
						0,
						line.size - 1,
						start.getOffset(),
						start.getType(),
						start.getLanguageIndex(),
					)
					token.setNextToken(current.getNextToken())
					return token
				}
			}
			current = current.getNextToken()
		}
		return null
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(JadxTokenMaker::class.java)

		private val CONTEXTUAL_KEYWORDS: Set<String> = Utils.constSet(
			"exports", "module", "non-sealed", "open", "opens", "permits", "provides", "record",
			"requires", "sealed", "to", "transitive", "uses", "var", "with", "yield",
		)
	}
}
