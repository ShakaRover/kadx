package jadx.plugins.input.aab.factories

import com.android.bundle.Files
import jadx.api.ResourceFile
import jadx.api.impl.SimpleCodeInfo
import jadx.api.plugins.resources.IResContainerFactory
import jadx.core.xmlgen.ResContainer
import java.io.InputStream

/**
 * 为 assets.pb 创建 ResContainer（内容即 protobuf 的 toString）。
 */
public class ProtoAssetsConfigResContainerFactory : IResContainerFactory {

	override fun create(resFile: ResourceFile, inputStream: InputStream): ResContainer? {
		if (!resFile.getOriginalName().endsWith("assets.pb")) {
			return null
		}

		val assetsConfig = Files.Assets.parseFrom(inputStream)
		val content = SimpleCodeInfo(assetsConfig.toString())
		return ResContainer.textResource(resFile.getDeobfName(), content)
	}
}
