package com.garden.joeyshapiro

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

private val json = Json { ignoreUnknownKeys = true; isLenient = true }
private const val ANSI_RESET = "\u001B[0m"
private const val ANSI_RED = "\u001B[31m" // error
private const val ANSI_GRAY = "\u001B[2m" // dull, not mine
private const val ANSI_YELLOW = "\u001B[33m" // warning
private const val ANSI_GREEN = "\u001B[32m" // all good
private const val ANSI_BLUE = "\u001B[34m" // user input, should be default
private const val ANSI_CYAN = "\u001B[36m" // user input
private const val ANSI_VAPOR = "\u001B[1;3;5;35;46m" // vapor effect bold;italic;slow blink;foreground magenta;background cyan wanted font, but eh

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
	print("$ANSI_CYAN$question [Y/n]: ")
	val input = readlnOrNull()
	val chosen = if (input.isNullOrEmpty()) "y" else input
	print(ANSI_RESET)
	return chosen.startsWith("y", true)
}

fun aptOption(question: String, choices: List<String>, default: Int?): Int? { // maybe use objects and determine what to print; mapping isnt hard
	// print the list
	choices.forEachIndexed { i, choice ->
		val color = if (i == default) ANSI_BLUE else ANSI_CYAN
		println("$color$i - $choice")
	}

	// make a choice
	print("$question [$default]: ")
	val input = readlnOrNull()

	print(ANSI_RESET)
	return if (input.isNullOrEmpty()) null else input.toIntOrNull()
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
	println(ANSI_VAPOR + "not vaporware, bitch" + ANSI_RESET)

	// create the output folder
	// try as value and try when
	try {
		Files.createDirectory(Paths.get(project))
	} catch (e: FileAlreadyExistsException) {
		if (Path(project).toFile().listFiles()!!.isNotEmpty()) { // most things don't say the folder is empty
			print(ANSI_CYAN)
			println("$project is not empty.")
			println("You can continue with the current folder, or create a subfolder.")

			if (!aptInput("Would you like to continue anyway")) {
				project = "$project/$modpackZip"
				println("Creating a new directory for the modpack at $project")
				Files.createDirectory(Paths.get(project))
			}
			print(ANSI_RESET)
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
				}
			}
		}
	}

	// unwrap manifest, only say something if it could _not_ be found
	if (possibleManifest == null) {
		println(ANSI_RED + "Error: Could not find manifest" + ANSI_RESET)
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
		println(ANSI_RED + "Error: Modlist and Manifest have different mods" + ANSI_RESET)
		return
	}

	val mods = mutableMapOf<Long, Mod>()
	manifest.files.forEachIndexed { i, file -> mods[file.projectID] = Mod(modlist[i], file) }

	// create mods folder
	try {
		Files.createDirectory(Paths.get("$project/mods"))
	} catch (e: FileAlreadyExistsException) { // accounts for update
		println(ANSI_GRAY + "mods folder already created" + ANSI_RESET)
	}

	// download each mod
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
				println("Could not find ${mod.value.listed.text}")
				val i = aptOption("Please one of the following as a replacement", files.data.map { it.fileName }, 0)!!
				file = files.data[i]
				println("Using ${files.data[i].fileName} instead")
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
			// could copy each byte and update, but files not big enough, cost so much to do; just copy _not_ download
			// for i in n file.write(i); pb.step()
			try {
				val n = jarUrl.openStream().use { Files.copy(it, Paths.get("$project/mods/${file.fileName}")) }
				if (n != file.fileLength) {
					println(ANSI_YELLOW + "Warning: ${file.fileName} Downloaded ${n}B, but should be ${file.fileLength}B" + ANSI_RESET)
				}
			} catch (e: FileAlreadyExistsException) {
				if (!shouldUpdate) {
					println(ANSI_GRAY + "Already downloaded ${file.fileName}" + ANSI_RESET)
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
				println(ANSI_RED + "${parts[0]} is currently not supported" + ANSI_RESET)
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
		print(ANSI_GRAY)
		val result = ProcessBuilder("java", "-jar", "$project/$jarForge", "--installServer", project)
			.redirectOutput(ProcessBuilder.Redirect.INHERIT)
			.redirectError(ProcessBuilder.Redirect.INHERIT)
			.start().waitFor()
		print(ANSI_RESET)

		if (result != 0) {
			println(ANSI_RED + "Server install failed" + ANSI_RESET)
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
			println(ANSI_YELLOW + "You must sign the EULA file before starting the server" + ANSI_RESET)
		}

		// cleanup
		Files.deleteIfExists(Paths.get("$project/$jarForge"))

		println(ANSI_GREEN + "The mod pack has successfully been created" + ANSI_RESET)
	} else {
		println(ANSI_GREEN + "The mod pack has successfully been updated" + ANSI_RESET)
	}
	println("https://github.com/JoeyShapiro/Cucumber")
}