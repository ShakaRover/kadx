package kadx.gui.ui.graphs

import com.kitfox.svg.SVGDiagram
import com.kitfox.svg.SVGUniverse
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.engine.Renderer
import kadx.core.utils.exceptions.KadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * 图形显示面板：负责把 DOT 字符串渲染成 SVG，再绘制到 [BufferedImage] 上。
 *
 * **做什么**：支持拖拽平移与滚轮缩放；首次打开时自适应窗口大小，之后保持比例。
 * 渲染失败时把错误文本区 [JTextArea] 作为唯一子组件显示。
 *
 * **自定义绘制**：重写 [paintComponent]，按当前平移/缩放对缓存图像做仿射变换。
 *
 * **线程模型**：保持原 Swing 模型——[setGraph] 内的自适应逻辑用 `SwingUtilities.invokeLater`。
 */
class GraphPanel(private val parentDialog: GraphDialog) : JPanel() {

	private val fullImageSize = Dimension()
	private val minimumScale = 0.1
	private val maximumScale = 10.0
	private var scale = 1.0
	private var translateX = 0.0
	private var translateY = 0.0
	private var lastDragPoint: Point? = null
	private var image: BufferedImage? = null
	private lateinit var renderer: Renderer
	private lateinit var svgDiagram: SVGDiagram

	init {
		val ma: MouseAdapter = GraphPanelMouseAdapter()
		addMouseListener(ma)
		addMouseMotionListener(ma)
		addMouseWheelListener(ma)
	}

	override fun paintComponent(g: Graphics) {
		super.paintComponent(g)
		val img = image
		if (img != null) {
			val g2d = g as Graphics2D
			val transform = AffineTransform()
			transform.translate(translateX * scale, translateY * scale)
			g2d.drawImage(img, transform, null)
		}
	}

	/** 解析并渲染新的 DOT 字符串；失败时在面板内显示错误。 */
	fun setGraph(dotString: String) {
		try {
			init(dotString)
		} catch (e: Exception) {
			LOG.error("Error parsing DOT string", e)
			invalidateImage(GraphDialog.graphError(e))
		}
	}

	/** 把当前图形导出为 SVG 文件。 */
	fun exportSVG(svgImage: File) {
		try {
			renderer.toFile(svgImage)
		} catch (e: IOException) {
			throw RuntimeException(e)
		}
	}

	private fun init(dotString: String) {
		image = null
		val bytes: ByteArray = try {
			ByteArrayOutputStream().use { bout ->
				renderer = Graphviz.fromString(dotString).width(width).render(Format.SVG)
				renderer.toOutputStream(bout)
				bout.toByteArray()
			}
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to render graph", e)
		}
		try {
			ByteArrayInputStream(bytes).use { input ->
				val universe = SVGUniverse()
				val uri: URI = universe.loadSVG(input, "//graph/")
				svgDiagram = universe.getDiagram(uri)
				svgDiagram.setIgnoringClipHeuristic(true)
				fullImageSize.setSize(svgDiagram.getWidth().toDouble(), svgDiagram.getHeight().toDouble())
			}
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to process SVG image", e)
		}
		parentDialog.enableMenu()
		removeAll()
		SwingUtilities.invokeLater {
			val width = getWidth().toDouble()
			val height = getHeight().toDouble()
			if (scale == 1.0) {
				// 打开对话框时自适应窗口（切换图形时保持当前缩放）
				scale = Math.min(width / fullImageSize.width.toDouble(), height / fullImageSize.height.toDouble())
			}
			renderGraphScaled()
			if (image == null) {
				return@invokeLater
			}
			// 将图像居中
			translateX = (width / 2.0 - fullImageSize.width * scale / 2.0) / scale
			translateY = (height / 2.0 - fullImageSize.height * scale / 2.0) / scale
			repaint()
		}
	}

	private fun renderGraphScaled() {
		try {
			if (fullImageSize.width * scale * fullImageSize.height * scale >= Int.MAX_VALUE) {
				scale = maximumScale
			}
			image = BufferedImage(
				(fullImageSize.width * scale).toInt(),
				(fullImageSize.height * scale).toInt(),
				BufferedImage.TYPE_INT_ARGB,
			)
			val imgG2d = checkNotNull(image).createGraphics()
			configGraphics(imgG2d)
			val transform = AffineTransform()
			transform.scale(scale, scale)
			imgG2d.setTransform(transform)
			svgDiagram.render(imgG2d)
		} catch (e: Exception) {
			LOG.error("Graph render failed: ", e)
			invalidateImage(GraphDialog.graphError(e))
		}
	}

	private fun configGraphics(graphics: Graphics2D) {
		graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
		graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
	}

	/** 用错误文本区替换图形内容，并禁用父窗口菜单。 */
	fun invalidateImage(errorMsg: JTextArea) {
		removeAll()
		add(errorMsg)
		image = null
		parentDialog.disableMenu()
		revalidate()
		repaint()
	}

	/** 鼠标交互：拖拽平移、滚轮缩放（围绕光标位置缩放）。 */
	private inner class GraphPanelMouseAdapter : MouseAdapter() {
		override fun mousePressed(e: MouseEvent) {
			lastDragPoint = e.getPoint()
		}

		override fun mouseDragged(e: MouseEvent) {
			if (image != null) {
				val p = e.getPoint()
				val last = lastDragPoint ?: return
				translateX += (p.x - last.x) / scale
				translateY += (p.y - last.y) / scale
				lastDragPoint = p
				repaint()
			}
		}

		override fun mouseWheelMoved(e: MouseWheelEvent) {
			if (image != null) {
				val prevScale = scale
				scale *= Math.pow(1.1, -e.getWheelRotation().toDouble())
				if (scale > maximumScale) {
					scale = maximumScale
				}
				if (scale < minimumScale) {
					scale = minimumScale
				}
				if (scale != prevScale) {
					val p = e.getPoint()
					val px = (p.x - translateX * prevScale) / prevScale
					val py = (p.y - translateY * prevScale) / prevScale
					translateX = p.x / scale - px
					translateY = p.y / scale - py
					renderGraphScaled()
					if (image == null) {
						return
					}
					repaint()
				}
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(GraphPanel::class.java)
	}
}
