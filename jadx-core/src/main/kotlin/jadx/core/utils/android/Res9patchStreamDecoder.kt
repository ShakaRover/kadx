package jadx.core.utils.android

import jadx.core.utils.exceptions.JadxRuntimeException
import java.awt.image.BufferedImage
import java.io.DataInput
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * Android `.9.png`（NinePatch）资源解码器。
 *
 * **背景**：`.9.png` 在普通 PNG 的四周各多出 1 像素的“黑线”用来描述可拉伸区域与内容边距。
 * 该解码器把 9-patch 的元数据重新画回图像边缘，输出一张可直接查看的普通 PNG。
 *
 * **来源**：改编自 Android apktool（Ryszard Wiśniewski）的实现。
 *
 * **Kotlin 转换说明**：
 * - 嵌套 [NinePatch] 与常量放入 `companion object`/嵌套类，均为私有实现细节；
 * - `NP_COLOR` 原为 `0xff000000`，Kotlin 中该字面量超出 Int 范围，写成等价的 `-0x1000000`；
 * - `ImageIO.read` 可能返回 null，但原 Java 在 `np == null` 时提前返回，故这里先判空再解引用。
 */
class Res9patchStreamDecoder {

	/**
	 * 解码输入流中的 9-patch 图像并写入 [out]。
	 *
	 * @return 成功解码返回 true；输入不是合法 9-patch 返回 false
	 */
	fun decode(`in`: InputStream, out: OutputStream): Boolean {
		try {
			val im: BufferedImage? = ImageIO.read(`in`)
			val np = getNinePatch(`in`) ?: return false
			val image = checkNotNull(im)
			val w = image.width
			val h = image.height

			val im2 = BufferedImage(w + 2, h + 2, BufferedImage.TYPE_INT_ARGB)
			im2.createGraphics().drawImage(image, 1, 1, w, h, null)

			// 画内容边距线
			drawHLine(im2, h + 1, np.padLeft + 1, w - np.padRight)
			drawVLine(im2, w + 1, np.padTop + 1, h - np.padBottom)

			// 画水平/垂直可拉伸区域（成对出现的 x/y 分割点）
			val xDivs = np.xDivs
			var i = 0
			while (i < xDivs.size - 1) {
				drawHLine(im2, 0, xDivs[i] + 1, xDivs[i + 1])
				i += 2
			}

			val yDivs = np.yDivs
			i = 0
			while (i < yDivs.size - 1) {
				drawVLine(im2, 0, yDivs[i] + 1, yDivs[i + 1])
				i += 2
			}

			ImageIO.write(im2, "png", out)
			return true
		} catch (e: Exception) {
			throw JadxRuntimeException("9patch image decode error", e)
		}
	}

	@Throws(IOException::class)
	private fun getNinePatch(`in`: InputStream): NinePatch? {
		val di = ExtDataInput(`in`)
		if (!find9patchChunk(di)) {
			return null
		}
		return NinePatch.decode(di)
	}

	/**
	 * 在 PNG 的分块（chunk）中寻找 9-patch 专用块 `npTc`。
	 *
	 * PNG 以 8 字节签名开头，之后每个 chunk 为 `[长度(4)][类型(4)][数据][CRC(4)]`。
	 */
	@Throws(IOException::class)
	private fun find9patchChunk(di: DataInput): Boolean {
		di.skipBytes(8)
		while (true) {
			val size: Int
			try {
				size = di.readInt()
			} catch (ex: IOException) {
				return false
			}
			if (di.readInt() == NP_CHUNK_TYPE) {
				return true
			}
			di.skipBytes(size + 4)
		}
	}

	private fun drawHLine(im: BufferedImage, y: Int, x1: Int, x2: Int) {
		for (x in x1..x2) {
			im.setRGB(x, y, NP_COLOR)
		}
	}

	private fun drawVLine(im: BufferedImage, x: Int, y1: Int, y2: Int) {
		for (y in y1..y2) {
			im.setRGB(x, y, NP_COLOR)
		}
	}

	/** 9-patch 元数据：四条内容边距 + 水平/垂直分割点数组。 */
	private class NinePatch(
		val padLeft: Int,
		val padRight: Int,
		val padTop: Int,
		val padBottom: Int,
		val xDivs: IntArray,
		val yDivs: IntArray,
	) {
		companion object {
			@Throws(IOException::class)
			fun decode(di: ExtDataInput): NinePatch {
				di.skipBytes(1)
				val numXDivs = di.readByte()
				val numYDivs = di.readByte()
				di.skipBytes(1)
				di.skipBytes(8)
				val padLeft = di.readInt()
				val padRight = di.readInt()
				val padTop = di.readInt()
				val padBottom = di.readInt()
				di.skipBytes(4)
				val xDivs = di.readIntArray(numXDivs.toInt())
				val yDivs = di.readIntArray(numYDivs.toInt())

				return NinePatch(padLeft, padRight, padTop, padBottom, xDivs, yDivs)
			}
		}
	}

	companion object {
		/** 9-patch 专用 chunk 类型 `npTc`。 */
		private const val NP_CHUNK_TYPE = 0x6e705463

		/** 画线使用的黑色（0xff000000 的等价有符号写法）。 */
		private const val NP_COLOR = -0x1000000
	}
}
