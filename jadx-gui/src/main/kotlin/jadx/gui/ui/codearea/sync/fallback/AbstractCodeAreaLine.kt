package jadx.gui.ui.codearea.sync.fallback

import jadx.gui.ui.codearea.AbstractCodeArea

/**
 * 代码区中某一行的抽象基类。
 *
 * **做什么**：缓存整行文本，并定义“声明行判定”“提取声明名”“找外层声明”等
 * 供回退同步使用的通用行为；Java / Smali 各有具体子类。
 *
 * **注意**：文本按 `\R`（任意换行）切分，所以 Kotlin 里必须用 `Regex("\\R")`，
 * 否则会按字面量 `\R` 切分。
 */
abstract class AbstractCodeAreaLine protected constructor(
	private val area: AbstractCodeArea,
	private val lineIndex: Int,
) {
	private val line: String = area.getText().split(Regex("\\R"))[lineIndex]

	fun getArea(): AbstractCodeArea = area

	fun getLineIndex(): Int = lineIndex

	/** 原始行文本（含缩进）。 */
	fun getStr(): String = line

	/** 去掉首尾空白后的行文本。 */
	fun getTrimmedStr(): String = line.trim()

	abstract fun getLineAt(lineIndex: Int): AbstractCodeAreaLine

	abstract fun isClassDeclaration(): Boolean

	abstract fun isMethodOrConstructorDeclaration(): Boolean

	abstract fun isFieldDeclaration(): Boolean

	abstract fun extractDeclaredMethodName(): String?

	abstract fun extractDeclaredClassName(): String?

	protected abstract fun createMethodDeclaration(): MethodDeclaration

	/**
	 * 返回本行所处的作用域声明：可能是自身，也可能是：
	 * - 若本行在方法内，则为外层方法声明；
	 * - 若本行是字段声明，则为外层类声明。
	 */
	fun getEnclosingScopeDeclaration(): IDeclaration {
		val decl = getDeclaration()
		if (decl != null) {
			return decl
		}
		for (i in lineIndex - 1 downTo 0) {
			val enclosing = getLineAt(i)
			if (enclosing.isScopeDeclarationLine()) {
				return checkNotNull(enclosing.getDeclaration())
			}
		}
		throw FallbackSyncException("No enclosing declaration found for $this")
	}

	/** 是否属于“作用域声明行”（类声明或方法 / 构造器声明）。 */
	fun isScopeDeclarationLine(): Boolean = isClassDeclaration() || isMethodOrConstructorDeclaration()

	/** 是否属于“声明行”（作用域声明或字段声明）。 */
	fun isDeclarationLine(): Boolean = isScopeDeclarationLine() || isFieldDeclaration()

	fun getDeclaration(): IDeclaration? {
		if (isClassDeclaration()) {
			return ClassDeclaration(this)
		}
		if (isMethodOrConstructorDeclaration()) {
			return createMethodDeclaration()
		}
		return null
	}

	override fun toString(): String = line
}
