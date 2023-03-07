
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.tongfei.progressbar.ProgressBar
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile


@Serializable
data class Manifest (
	val minecraft: Minecraft,
	val manifestType: String,
	val manifestVersion: Long,
	val name: String,
	val version: String,
	val author: String,
	val files: List<ModFile>,
	val overrides: String
)

@Serializable
data class ModFile (
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

data class Mod (
	val listed: ModListed,
	val file: ModFile
)

@Serializable
data class CurseFiles (
	val data: List<Datum>,
	val pagination: Pagination
)

@Serializable
data class Datum (
	val id: Long,
	val childFileType: Long,
	val dateCreated: String,
	val dateModified: String,
	val fileLength: Long,
	val fileName: String,
	val gameVersions: List<String>,

	val gameVersionTypeIds: List<Long>,

	val projectId: Long,

	val releaseType: Long,
	val status: Long,
	val totalDownloads: Long,
	val uploadSource: Long,
	val hasServerPack: Boolean,
	val additionalFilesCount: Long,
	val isEarlyAccessContent: Boolean
)

@Serializable
data class Pagination (
	val index: Long,
	val pageSize: Long,
	val totalCount: Long
)

fun main() {
	println("Hello, Kotlin/Java!")
	println("bitch")
	var manifest: Manifest? = null // isnt initialized, makes sense
	var modlist: List<ModListed> = mutableListOf<ModListed>()
	val url = "https://mediafilez.forgecdn.net/files/"
	// best i can do is hard code. should get site to work
	// TODO find how it gets list, or get page
	// this is not super stable but it works
	// the list is used and nothing should have reason to change
	// the list of versions is from checking the different versions of a mod available on different versions
	// the api call to get mod details is from the same page (files of a mod)
	// the download is a bypass of cloudflare
	val versions = mutableMapOf<String, Int>()
	versions["1.19.3"] = 9550
	versions["1.19.2"] = 9366
	versions["1.19.1"] = 9259
	versions["1.19"] = 9186
	versions["1.18.2"] = 9008
	versions["1.18.1"] = 8857
	versions["1.18"] = 8830
	versions["1.16.5"] = 0
	versions["1.12.2"] = 6756

	ZipFile("Winter_2022-1.1.1.zip").use { zip ->
		zip.entries().asSequence().forEach { entry ->
//			zip.getInputStream(entry).use { input ->
//				File(entry.name).outputStream().use { output ->
//					input.copyTo(output)
//				}
//			}
			if (entry.name == "manifest.json" || entry.name == "modlist.html") {
				println("found ${entry.name}")
				val stream = zip.getInputStream(entry)
				val content = stream.readAllBytes()

				if (entry.name == "manifest.json") {
					manifest = Json.decodeFromString<Manifest>(String(content))
					println("Found manifest for ${manifest!!.name} with ${manifest!!.files.size} mods")
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
					println("Found modlist for ${modlist.size} mods")
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
	// TODO unwrap
	println("${modlist.size} == ${manifest!!.files.size}")
	val mods = mutableMapOf<Long, Mod>()
	manifest!!.files.forEachIndexed { i, file -> mods[file.projectID] = Mod(modlist[i], file) }

	// download each mod
	ProgressBar("Downloading", mods.size.toLong()).use { pb ->
		for	(mod in mods) {
			pb.extraMessage = mod.value.listed.text
			// they think theyre so clever
			// maybe they are
			// cant go on main site
			val id_path = mod.value.file.fileID.toString()
			val details = "https://beta.curseforge.com/api/v1/mods/${mod.value.file.projectID}/files?pageIndex=0&pageSize=100sort=dateCreated&sortDescending=true&gameVersionId=${versions[manifest!!.minecraft.version]}"
			val input = URL(details).openStream()
			val reader = InputStreamReader(input, "UTF-8")
			val json = Json { ignoreUnknownKeys = true; isLenient = true }
			val files = json.decodeFromString<CurseFiles>(reader.readText())
			reader.close()
			input.close()
			var file = files.data.firstOrNull { it.id == mod.value.file.fileID }
			if (file == null) {
				file = files.data[0]
				println("Could not find ${mod.value.listed.text}. Using ${files.data[0].fileName} instead.")
			}
			// https://mediafilez.forgecdn.net/files/3914/491/balm-3.2.0%2B0.jar
			// https://mediafilez.forgecdn.net/files/3914/491/balm-3.2.0%252B0.jar
			// https://mediafilez.forgecdn.net/files/3914/491/balm-3.2.0%252B0.jar
			val encFilename = java.net.URLEncoder.encode(file.fileName, StandardCharsets.UTF_8.toString())
			val jarUrl = URL("$url${id_path.subSequence(0, 4)}/${id_path.subSequence(4, 7)}/${encFilename}")
			//val n = jarUrl.openStream().use { Files.copy(it, Paths.get("./${file.fileName}")) }
			// dont actually need jsoup. also, fails to encode properly
//			val jar = Jsoup.connect("$url${id_path.subSequence(0, 4)}/${id_path.subSequence(4, 7)}/${encFilename}")
//				.ignoreContentType(true).execute();

			pb.step()
//			break
		}
	}
}