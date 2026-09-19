package jadx.plugins.tools.utils

import jadx.commons.app.JadxCommonFiles
import jadx.core.utils.files.FileUtils.makeDirs
import java.nio.file.Path

object PluginFiles {
	val PLUGINS_DIR: Path = JadxCommonFiles.getConfigDir().resolve("plugins")
	val PLUGINS_JSON: Path = PLUGINS_DIR.resolve("plugins.json")
	val INSTALLED_DIR: Path = PLUGINS_DIR.resolve("installed")
	val DROPINS_DIR: Path = PLUGINS_DIR.resolve("dropins")

	val PLUGINS_LIST_CACHE: Path = JadxCommonFiles.getCacheDir().resolve("plugin-list.json")

	init {
		makeDirs(INSTALLED_DIR)
		makeDirs(DROPINS_DIR)
	}
}
