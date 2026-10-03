package jadx.gui.report

import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.ui.ActionMessageBox
import jadx.gui.utils.ui.ActionMessageBox.Action
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.util.ArrayList

/**
 * 文件 I/O 异常的统一提示框。
 *
 * **做什么**：根据 [IOException] 的具体类型给出更友好的本地化文案
 * （文件不存在 / 无权限 / 其他），并提供「上报」与「关闭」两个按钮。
 */
object IOExceptionMessageBox {

	fun show(mainWindow: MainWindow, excData: ExceptionData) {
		val ioExc: IOException = excData.iOExc ?: return
		val message: String = when {
			ioExc is FileNotFoundException || ioExc is NoSuchFileException ->
				String.format(NLS.str("io_error_dialog.file_not_found"), ioExc.message)

			ioExc is AccessDeniedException ->
				String.format(NLS.str("io_error_dialog.access_denied"), ioExc.message)

			else -> ioExc.javaClass.simpleName + ": " + ioExc.message
		}
		val actions: MutableList<Action> = ArrayList(2)
		actions.add(Action(NLS.str("io_error_dialog.report"), Runnable { ExceptionDialog.show(mainWindow, excData) }))
		actions.add(Action(NLS.str("io_error_dialog.close"), UiUtils.EMPTY_RUNNABLE))
		ActionMessageBox(mainWindow, NLS.str("io_error_dialog.title"), message, actions).show()
	}
}
