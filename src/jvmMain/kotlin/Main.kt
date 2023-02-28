

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.zip.ZipFile

@Serializable
data class Manifest (
	val minecraft: Minecraft,
	val manifestType: String,
	val manifestVersion: Long,
	val name: String,
	val version: String,
	val author: String,
	val files: List<File>,
	val overrides: String
)

@Serializable
data class File (
	val projectID: Long,
	val fileID: Long,
	val required: Boolean
)

@Serializable
data class Minecraft (
	val version: String,
	val modLoaders: List<ModLoader>
)

@Serializable
data class ModLoader (
	val id: String,
	val primary: Boolean
)

data class ModListed (
	val full: String,
	val link: String,
	val text: String
)

fun main() {
	println("Hello, Kotlin/Java!")
	println("bitch")
	var manifest: Manifest
	var modlist: List<ModListed>

	ZipFile("Winter_2022-1.1.1.zip").use { zip ->
		zip.entries().asSequence().forEach { entry ->
//			zip.getInputStream(entry).use { input ->
//				File(entry.name).outputStream().use { output ->
//					input.copyTo(output)
//				}
//			}
			if (entry.name == "manifest.json" || entry.name == "modlist.html") {
				println(entry.name)
				val stream = zip.getInputStream(entry)
				val content = stream.readAllBytes()

				if (entry.name == "manifest.json") {
					manifest = Json.decodeFromString<Manifest>(String(content))
					println("Found manifest for ${manifest.name}")
				} else if (entry.name == "modlist.html") {
					val html = String(content)
					// TODO for now use regex, when i do validation use jsoup
					// go to files, check pid, download. do multiple at once
					val regex = "<a href=\"(.+)\">(.+)</a>".toRegex()
					modlist = regex.findAll(html).map {
						ModListed(
							full = it.groupValues[0],
							link = it.groupValues[1],
							text = it.groupValues[2]
						)
					}.toList()
					println("Found modlist for ${modlist.size}")
				}
			}
		}
	}

	// (optional) create a map of pid->href (check page for pid) 1:1
	//? they should be in order though
	// go to to mapped files of item in json
	// (optional) check pid matches
	// go to download of fid
	// TODO check pid

}