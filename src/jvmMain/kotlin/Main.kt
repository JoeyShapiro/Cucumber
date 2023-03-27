package com.garden.joeyshapiro

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.tongfei.progressbar.ProgressBar
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
	versions["1.16.5"] = 0 // TODO add
	versions["1.12.2"] = 6756

	return versions
}

fun parseArgs(args: Array<String>): MutableMap<String, String> {
	val parsedArgs = mutableMapOf<String, String>()
	var flagged = false

	// TODO invalid args
	for (i in args.indices) {
		// if the prev was a flag, then
		if (flagged) {
			flagged = false
			continue
		}

		// if a flag, insert the values in the dict
		if (args[i].startsWith("--")) {
			parsedArgs[args[i].removePrefix("--")] = args[i+1]
			flagged = true
		} else { // if just loose value
			parsedArgs[i.toString()] = args[i]
		}
	}

	return parsedArgs
}

fun aptInput(question: String): Boolean {
	print("$question [Y/n]: ")
	val input = readlnOrNull()
	val chosen = if (input.isNullOrEmpty()) "y" else input
	return chosen.startsWith("y", true)
}

fun main(args: Array<String>) {
	println("Hello, Kotlin/Java!")
	println("bitch")
	// TODO update
	// use out as project folder
	// create the folder for their server project
	val argMap = parseArgs(args)
	val modpackZip = argMap["0"] ?: "" // TODO something better
	val project = argMap["out"] ?: argMap["o"] ?: "."

	// create the output folder
	// try as value and try when
	try {
		Files.createDirectory(Paths.get(project))
	} catch (e: FileAlreadyExistsException) {
		if (Path(project).toFile().listFiles()!!.isNotEmpty()) { // most things dont say the folder is empty
			println("$project is not empty.")
			println("You can continue with the current folder, or create a subfolder.")

			if (!aptInput("Would you like to continue anyway")) {
				// TODO add new folder
				throw NotImplementedError("Adding a new folder is not currently implemented.")
			}
		}
	}

	var manifest: Manifest? = null // isnt initialized, makes sense
	var modlist: List<ModListed> = mutableListOf<ModListed>()
	val url = "https://mediafilez.forgecdn.net/files"
	// best i can do is hard code. should get site to work
	// this is not super stable but it works
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
					manifest = Json.decodeFromString<Manifest>(String(content))
					println("Found manifest for ${manifest!!.name} with ${manifest!!.files.size} mods")
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

	// create mods folder
	try {
		Files.createDirectory(Paths.get("$project/mods"))
	} catch (e: FileAlreadyExistsException) {
		println("mods folder already created")
	}

	// download each mod
	println("Downloading ${mods.size} mods")
	// TODO timer for each mod, accept insteads, size, ..., throws for same file, access, ...
	ProgressBar("Downloading", mods.size.toLong()).use { pb ->
		for	(mod in mods) {
			pb.extraMessage = mod.value.listed.text

			// get all the versions available for this mod
			val details = "https://beta.curseforge.com/api/v1/mods/${mod.value.file.projectID}/files?pageIndex=0&pageSize=100sort=dateCreated&sortDescending=true&gameVersionId=${versions[manifest!!.minecraft.version]}"
			var files: CurseFiles? = null
			URL(details).openStream().use { input ->
				InputStreamReader(input, "UTF-8").use { reader ->
					files = json.decodeFromString<CurseFiles>(reader.readText())
				}
			}

			// find the file needed
			var file = files!!.data.firstOrNull { it.id == mod.value.file.fileID }
			if (file == null) {
				file = files!!.data[0]
				println("Could not find ${mod.value.listed.text}. Using ${files!!.data[0].fileName} instead.")
			}

			// download the file
			val encFilename = URLEncoder.encode(file.fileName, StandardCharsets.UTF_8.toString())
			val idPath = mod.value.file.fileID.toString()
			// get the id folders it's in
			// this is because they trim 0's
			val major = idPath.subSequence(0, 4).toString().toInt()
			val minor = idPath.subSequence(4, 7).toString().toInt()
			val jarUrl = URL("$url/$major/$minor/$encFilename")
			val n = jarUrl.openStream().use { Files.copy(it, Paths.get("$project/mods/${file.fileName}")) }
			pb.step()
			break
		}
	}

	// bypasses adfoc
	// maybe find a way to use adfoc for payment
	// and the flex
	// https://maven.minecraftforge.net/net/minecraftforge/forge/1.18.2-40.1.68/forge-1.18.2-40.1.68-installer.jar
	println("Downloading ${manifest!!.minecraft.modLoaders.size} modloaders")
	for (modloader in manifest!!.minecraft.modLoaders) {
		// check the type
		val parts = modloader.id.split('-')
		if (parts[0] != "forge") {
			println("${parts[0]} is currently not supported")
			continue
		}

		// download the file
		val jarName = "${parts[0]}-${manifest!!.minecraft.version}-${parts[1]}-installer.jar"
		println("Downloading $jarName")
		val jarUrl = URL("https://maven.minecraftforge.net/net/minecraftforge/forge/${manifest!!.minecraft.version}-${parts[1]}/${jarName}")
		jarUrl.openStream().use { Files.copy(it, Paths.get("${project}/${jarName}")) }
	}

	// dry run forge launcher

	// create run.sh

	println("The mod pack has successfully been created")
}