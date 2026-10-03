package jadx.gui.settings.data

import java.awt.Point

/**
 * 视图坐标点（编辑器滚动位置），用于项目文件的持久化。
 *
 * **做什么**：等价于 [java.awt.Point]，但字段名固定为 `x` / `y` 且不依赖 AWT 的序列化行为，
 * 便于 Gson 在项目 `.jadx` 文件中读写。
 *
 * **为什么保留显式 getter/setter**：原 Java 调用方按 `getX()` / `setY()` 访问，
 * 字段名也参与持久化格式，故保持字段名与访问器方法名不变。
 */
class ViewPoint {

	private var x: Int = 0
	private var y: Int = 0

	constructor() : this(0, 0)

	constructor(p: Point) : this(p.x, p.y)

	constructor(x: Int, y: Int) {
		this.x = x
		this.y = y
	}

	fun toPoint(): Point = Point(x, y)

	fun getX(): Int = x

	fun setX(x: Int) {
		this.x = x
	}

	fun getY(): Int = y

	fun setY(y: Int) {
		this.y = y
	}

	override fun toString(): String = "ViewPoint{$x, $y}"
}
