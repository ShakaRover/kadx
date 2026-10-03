package jadx.gui.ui.hexviewer

/*
 * Copyright (C) ExBin Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.exbin.bined.CodeAreaSection
import org.exbin.bined.highlight.swing.NonAsciiCodeAreaColorAssessor
import org.exbin.bined.highlight.swing.NonprintablesCodeAreaAssessor
import org.exbin.bined.highlight.swing.SearchCodeAreaColorAssessor
import org.exbin.bined.swing.CodeAreaCharAssessor
import org.exbin.bined.swing.CodeAreaColorAssessor
import org.exbin.bined.swing.CodeAreaPaintState
import java.awt.Color
import java.util.Optional

/**
 * 二进制编辑器的颜色评估器：在 ExBin 内置评估器之上叠加「可注册的着色修饰器」。
 *
 * **做什么**：内部用责任链把 `NonAscii -> Nonprintables -> Search` 三层评估器串起来，
 * 然后优先询问 [priorityColorModifiers]（高优先级），再询问 [colorModifiers]（普通），
 * 最后回落到父评估器。这样 jadx 可以给特定位置单独上色（例如选中、搜索结果）。
 *
 * @author ExBin Project (https://exbin.org)
 */
class BinEdCodeAreaAssessor(colorAssessor: CodeAreaColorAssessor?, charAssessor: CodeAreaCharAssessor?) :
	CodeAreaColorAssessor,
	CodeAreaCharAssessor {

	private val priorityColorModifiers: MutableList<PositionColorModifier> = ArrayList()
	private val colorModifiers: MutableList<PositionColorModifier> = ArrayList()

	private val parentColorAssessor: CodeAreaColorAssessor?
	private val parentCharAssessor: CodeAreaCharAssessor?

	init {
		val nonAsciiCodeAreaColorAssessor = NonAsciiCodeAreaColorAssessor(colorAssessor)
		val nonprintablesCodeAreaAssessor = NonprintablesCodeAreaAssessor(nonAsciiCodeAreaColorAssessor, charAssessor)
		val searchCodeAreaColorAssessor = SearchCodeAreaColorAssessor(nonprintablesCodeAreaAssessor)
		parentColorAssessor = searchCodeAreaColorAssessor
		parentCharAssessor = nonprintablesCodeAreaAssessor
	}

	fun addColorModifier(colorModifier: PositionColorModifier) {
		colorModifiers.add(colorModifier)
	}

	fun removeColorModifier(colorModifier: PositionColorModifier) {
		colorModifiers.remove(colorModifier)
	}

	fun addPriorityColorModifier(colorModifier: PositionColorModifier) {
		priorityColorModifiers.add(colorModifier)
	}

	fun removePriorityColorModifier(colorModifier: PositionColorModifier) {
		priorityColorModifiers.remove(colorModifier)
	}

	override fun startPaint(codeAreaPaintState: CodeAreaPaintState) {
		for (colorModifier in priorityColorModifiers) {
			colorModifier.resetColors()
		}
		for (colorModifier in colorModifiers) {
			colorModifier.resetColors()
		}
		parentColorAssessor?.startPaint(codeAreaPaintState)
	}

	override fun getPositionBackgroundColor(
		rowDataPosition: Long,
		byteOnRow: Int,
		charOnRow: Int,
		section: CodeAreaSection,
		inSelection: Boolean,
	): Color? {
		for (colorModifier in priorityColorModifiers) {
			val positionBackgroundColor =
				colorModifier.getPositionBackgroundColor(rowDataPosition, byteOnRow, charOnRow, section, inSelection)
			if (positionBackgroundColor != null) {
				return positionBackgroundColor
			}
		}

		if (!inSelection) {
			for (colorModifier in colorModifiers) {
				val positionBackgroundColor =
					colorModifier.getPositionBackgroundColor(rowDataPosition, byteOnRow, charOnRow, section, inSelection)
				if (positionBackgroundColor != null) {
					return positionBackgroundColor
				}
			}
		}

		return parentColorAssessor?.getPositionBackgroundColor(rowDataPosition, byteOnRow, charOnRow, section, inSelection)
	}

	override fun getPositionTextColor(
		rowDataPosition: Long,
		byteOnRow: Int,
		charOnRow: Int,
		section: CodeAreaSection,
		inSelection: Boolean,
	): Color? {
		for (colorModifier in priorityColorModifiers) {
			val positionTextColor = colorModifier.getPositionTextColor(rowDataPosition, byteOnRow, charOnRow, section, inSelection)
			if (positionTextColor != null) {
				return positionTextColor
			}
		}

		if (!inSelection) {
			for (colorModifier in colorModifiers) {
				val positionTextColor = colorModifier.getPositionTextColor(rowDataPosition, byteOnRow, charOnRow, section, inSelection)
				if (positionTextColor != null) {
					return positionTextColor
				}
			}
		}

		return parentColorAssessor?.getPositionTextColor(rowDataPosition, byteOnRow, charOnRow, section, inSelection)
	}

	override fun getPreviewCharacter(rowDataPosition: Long, byteOnRow: Int, charOnRow: Int, section: CodeAreaSection): Char = parentCharAssessor?.getPreviewCharacter(rowDataPosition, byteOnRow, charOnRow, section) ?: ' '

	override fun getPreviewCursorCharacter(
		rowDataPosition: Long,
		byteOnRow: Int,
		charOnRow: Int,
		cursorData: ByteArray,
		cursorDataLength: Int,
		section: CodeAreaSection,
	): Char = parentCharAssessor
		?.getPreviewCursorCharacter(rowDataPosition, byteOnRow, charOnRow, cursorData, cursorDataLength, section)
		?: ' '

	override fun getParentCharAssessor(): Optional<CodeAreaCharAssessor> = Optional.ofNullable(parentCharAssessor)

	override fun getParentColorAssessor(): Optional<CodeAreaColorAssessor> = Optional.ofNullable(parentColorAssessor)

	/** 位置着色修饰器：可对某个位置单独指定背景色 / 文字色。 */
	interface PositionColorModifier {

		fun getPositionBackgroundColor(
			rowDataPosition: Long,
			byteOnRow: Int,
			charOnRow: Int,
			section: CodeAreaSection,
			inSelection: Boolean,
		): Color?

		fun getPositionTextColor(
			rowDataPosition: Long,
			byteOnRow: Int,
			charOnRow: Int,
			section: CodeAreaSection,
			inSelection: Boolean,
		): Color?

		fun resetColors()
	}
}
