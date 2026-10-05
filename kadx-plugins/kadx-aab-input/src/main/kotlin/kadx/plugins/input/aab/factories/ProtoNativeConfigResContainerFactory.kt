package kadx.plugins.input.aab.factories

import com.android.bundle.Files
import kadx.api.ResourceFile
import kadx.api.impl.SimpleCodeInfo
import kadx.api.plugins.resources.IResContainerFactory
import kadx.core.xmlgen.ResContainer
import java.io.InputStream

/**
 * 为 native.pb 创建 ResContainer（内容即 protobuf 的 toString）。
 */
public class ProtoNativeConfigResContainerFactory : IResContainerFactory {

	override fun create(resFile: ResourceFile, inputStream: InputStream): ResContainer? {
		if (!resFile.getOriginalName().endsWith("native.pb")) {
			return null
		}

		val nativeConfig = Files.NativeLibraries.parseFrom(inputStream)
		val content = SimpleCodeInfo(nativeConfig.toString())
		return ResContainer.textResource(resFile.getDeobfName(), content)
	}
}
