import java.util.zip.ZipFile

fun main() {
	println("Hello, Kotlin/Java!")
	println("bitch")

	ZipFile("Winter_2022-1.1.1.zip").use { zip ->
		zip.entries().asSequence().forEach { entry ->
//			zip.getInputStream(entry).use { input ->
//				File(entry.name).outputStream().use { output ->
//					input.copyTo(output)
//				}
//			}
			println(entry.name)
		}
	}

	// (optional) create a map of pid->href (check page for pid) 1:1
	//? they should be in order though
	// go to to mapped files of item in json
	// (optional) check pid matches
	// go to download of fid
}