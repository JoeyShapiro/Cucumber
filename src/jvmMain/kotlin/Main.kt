package com.garden.joeyshapiro

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.tongfei.progressbar.ProgressBar
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile
import kotlin.io.path.Path

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
	//val childFileType: Long,
	val dateCreated: String,
	val dateModified: String,
	val fileLength: Long,
	val fileName: String,
	val gameVersions: List<String>,

	val gameVersionTypeIds: List<Long>,

	//val projectId: Long,

	val releaseType: Long,
	//val status: Long,
	val totalDownloads: Long,
	//val uploadSource: Long,
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

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun mcVersions(): MutableMap<String, Int> {
	val versions = mutableMapOf<String, Int>()

	versions["1.19.3"] = 9550
	versions["1.19.2"] = 9366
	versions["1.19.1"] = 9259
	versions["1.19"] = 9186
	versions["1.18.2"] = 9008
	versions["1.18.1"] = 8857
	versions["1.18"] = 8830
	versions["1.16.5"] = 8203
	versions["1.12.2"] = 6756

	return versions
}

fun aptInput(question: String): Boolean {
	print("$question [Y/n]: ")
	val input = readlnOrNull()
	val chosen = if (input.isNullOrEmpty()) "y" else input
	return chosen.startsWith("y", true)
}

fun main(args: Array<String>) {
	val flagParser = FlagParser(args)
	// parses each flag in code
	// if flag is found, set the value
	val modpackZip = flagParser.flagNone(0, "", "The zip of the modpack import.")
	var project = flagParser.flagString("out", "o", ".", "The project folder to use. If stuff inside, will create subfolder.")
	val shouldSign = flagParser.flagBool("sign", "s", "If set, will sign eula.txt.")
	val shouldUpdate = flagParser.flagBool("update", "u", "If set, will update the selected modpack")
	// check for help or invalid, print and return
	val helpText = flagParser.parse()
	if (helpText != null) {
		println(helpText)
		return
	}

	// needed stuff
	println("Hello, Kotlin/Java!")
	println("bitch")

	// create the output folder
	// try as value and try when
	try {
		Files.createDirectory(Paths.get(project))
	} catch (e: FileAlreadyExistsException) {
		if (Path(project).toFile().listFiles()!!.isNotEmpty()) { // most things don't say the folder is empty
			println("$project is not empty.")
			println("You can continue with the current folder, or create a subfolder.")

			if (!aptInput("Would you like to continue anyway")) {
				project = "$project/$modpackZip"
				println("Creating a new directory for the modpack at $project")
				Files.createDirectory(Paths.get(project))
			}
		}
	}

	var possibleManifest: Manifest? = null // isn't initialized, makes sense. _might_ not be there; or throw saying it's not set yet
	lateinit var manifest: Manifest
	var modlist: List<ModListed> = mutableListOf()
	val url = "https://mediafilez.forgecdn.net/files"
	// best I can do is hard code. should get site to work
	// this is not super stable, but it works
	// the list is used and nothing should have reason to change
	// the list of versions is from checking the different versions of a mod available on different versions
	// the api call to get mod details is from the same page (files of a mod)
	// the download is a bypass of cloudflare
	val versions = mcVersions()

	ZipFile(modpackZip).use { zip ->
		zip.entries().asSequence().forEach { entry ->
			if (entry.name == "manifest.json" || entry.name == "modlist.html") {
				println("found ${entry.name}")
				val stream = zip.getInputStream(entry)
				val content = stream.readAllBytes()

				if (entry.name == "manifest.json") {
					possibleManifest = Json.decodeFromString<Manifest>(String(content))
				} else if (entry.name == "modlist.html") {
					val html = String(content)
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

	// unwrap manifest, only say something if it could _not_ be found
	if (possibleManifest == null) {
		println("Error: Could not find manifest")
		return
	} else { // don't care what they think, its set
		manifest = possibleManifest!!
	}

	// (optional) create a map of pid->href (check page for pid) 1:1
	//? they should be in order though
	// go to mapped files of item in json
	// (optional) check pid matches
	// go to download of fid
	if (modlist.size != manifest.files.size) {
		println("Modlist and Manifest have different mods")
		return
	}

	val mods = mutableMapOf<Long, Mod>()
	manifest.files.forEachIndexed { i, file -> mods[file.projectID] = Mod(modlist[i], file) }

	// create mods folder
	try {
		Files.createDirectory(Paths.get("$project/mods"))
	} catch (e: FileAlreadyExistsException) { // accounts for update
		println("mods folder already created")
	}

	// download each mod
	println("Downloading ${mods.size} mods")
	// TODO timer for each mod, accept insteads, size, ..., throws for same file, access, ...
	ProgressBar("Downloading", mods.size.toLong()).use { pb ->
		for	(mod in mods) {
			pb.extraMessage = mod.value.listed.text

			// get all the versions available for this mod
			val details = "https://beta.curseforge.com/api/v1/mods/${mod.value.file.projectID}/files?pageIndex=0&pageSize=100sort=dateCreated&sortDescending=true&gameVersionId=${versions[manifest.minecraft.version]}"
			lateinit var files: CurseFiles
			URL(details).openStream().use { input ->
				InputStreamReader(input, "UTF-8").use { reader ->
					files = json.decodeFromString(reader.readText())
				}
			}

			// find the file needed
			var file = files.data.firstOrNull { it.id == mod.value.file.fileID }
			if (file == null) {
				file = files.data[0]
				println("Could not find ${mod.value.listed.text}. Using ${files.data[0].fileName} instead.")
			}

			// download the file
			val encFilename = URLEncoder.encode(file.fileName, StandardCharsets.UTF_8.toString())
			val idPath = mod.value.file.fileID.toString()
			// get the id folders it's in
			// this is because they trim 0's
			val major = idPath.subSequence(0, 4).toString().toInt()
			val minor = idPath.subSequence(4, 7).toString().toInt()
			val jarUrl = URL("$url/$major/$minor/$encFilename")
			// can not easily show each byte being downloaded, not worth it, maybe another time
			try {
				val n = jarUrl.openStream().use { Files.copy(it, Paths.get("$project/mods/${file.fileName}")) }
				if (n != file.fileLength) {
					println("Warning: ${file.fileName} Downloaded ${n}B, but should be ${file.fileLength}B")
				}
			} catch (e: FileAlreadyExistsException) {
				if (!shouldUpdate) {
					println("Already downloaded ${file.fileName}")
				}
			}

			pb.step()
		}
	}

	if (!shouldUpdate) {
		// bypasses adfoc
		// maybe find a way to use adfoc for payment
		// and the flex
		// https://maven.minecraftforge.net/net/minecraftforge/forge/1.18.2-40.1.68/forge-1.18.2-40.1.68-installer.jar
		println("Downloading ${manifest.minecraft.modLoaders.size} modloaders")
		// late init can be used instead of null, but not val
		lateinit var jarForge: String
		for (modloader in manifest.minecraft.modLoaders) {
			// check the type
			val parts = modloader.id.split('-')
			if (parts[0] != "forge") {
				println("${parts[0]} is currently not supported")
				continue
			}

			// download the file
			jarForge = "${parts[0]}-${manifest.minecraft.version}-${parts[1]}-installer.jar"
			println("Downloading $jarForge")
			val jarUrl = URL("https://maven.minecraftforge.net/net/minecraftforge/forge/${manifest.minecraft.version}-${parts[1]}/${jarForge}")
			jarUrl.openStream().use { Files.copy(it, Paths.get("${project}/${jarForge}")) }
		}

		// install the server
		// java -jar forge.jar --installServer "./"
		println("Installing Forge Server")
		val result = ProcessBuilder("java", "-jar", "$project/$jarForge", "--installServer", project)
			.redirectOutput(ProcessBuilder.Redirect.INHERIT)
			.redirectError(ProcessBuilder.Redirect.INHERIT)
			.start().waitFor()

		if (result != 0) {
			println("Server install failed")
		}

		// fix run script
		// good enough; I feel you can have it run anywhere though
		var text = File("$project/run.sh").readLines()
		File("$project/run.sh").printWriter().use {
			for (line in text) {
				if (line.startsWith("java")) {
					it.println("dir=\"\$( cd \"\$( dirname \"\${BASH_SOURCE[0]}\" )\" && pwd)\"")
					it.println("pushd \$dir")
					it.println(line)
					it.println("popd")
				} else { // if we don't care about the line
					it.println(line)
				}
			}
		}

		// dry run forge launcher (they give a run.sh :D)
		ProcessBuilder("$project/run.sh").start().waitFor()

		// option to maybe sign eula
		if (shouldSign) {
			text = File("$project/eula.txt").readLines()
			File("$project/eula.txt").printWriter().use {
				for (line in text) {
					if (line.startsWith("eula")) {
						it.println("eula=true")
					} else {
						it.println(line)
					}
				}
			}
		} else {
			println("You must sign the EULA file before starting the server")
		}

		// cleanup
		Files.deleteIfExists(Paths.get("$project/$jarForge"))

		println("The mod pack has successfully been created")
	} else {
		println("The mod pack has successfully been updated")
	}

}