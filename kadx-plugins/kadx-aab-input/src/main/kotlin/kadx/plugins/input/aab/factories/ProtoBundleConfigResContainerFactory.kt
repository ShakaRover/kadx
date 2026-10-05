package kadx.plugins.input.aab.factories

import com.android.bundle.Config.BundleConfig
import kadx.api.ResourceFile
import kadx.api.impl.SimpleCodeInfo
import kadx.api.plugins.resources.IResContainerFactory
import kadx.core.xmlgen.ResContainer
import java.io.InputStream

/**
 * 为 BundleConfig.pb 创建 ResContainer（内容即 protobuf 的 toString）。
 */
public class ProtoBundleConfigResContainerFactory : IResContainerFactory {

	override fun create(resFile: ResourceFile, inputStream: InputStream): ResContainer? {
		if (!resFile.getOriginalName().endsWith("BundleConfig.pb")) {
			return null
		}
		val bundleConfig = BundleConfig.parseFrom(inputStream)
		val content = SimpleCodeInfo(bundleConfig.toString())
		return ResContainer.textResource(resFile.getDeobfName(), content)
	}
}
