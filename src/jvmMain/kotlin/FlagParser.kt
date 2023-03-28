package com.garden.joeyshapiro

class FlagParser(programArgs: Array<String>) {
	private val args: Array<String> = programArgs
	private val flags = mutableMapOf<String, Flag>() // dictionary is always nicer, i think

	fun parseArgs(args: Array<String>): MutableMap<String, String> {
		val parsedArgs = mutableMapOf<String, String>()
		var flagged = false

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
			} else if (args[i].startsWith("-")) {
				parsedArgs[args[i].removePrefix("-")] = args[i+1]
				flagged = true
			} else { // if just loose value
				parsedArgs[i.toString()] = args[i]
			}
		}

		return parsedArgs
	}

	fun getHelp(): String {
		var helpText = ""

		for (flag in flags) {
			helpText += "${flag.key}\n"
			helpText += "\tflag/shorthand: --${flag.value.flag} | -${flag.value.shorthand}\n"
			helpText += "\tdefault: ${flag.value.default}\n"
			helpText += "\tdescription: ${flag.value.help}\n"
			helpText += "\n"
		}

		return helpText
	}

	private fun maybeHelp(): String? {
		var shouldHelp = false

		// check if the help flag is set
		for (i in args.indices) {
			if (args[i] == "--help" || args[i] == "-h") {
				shouldHelp = true
				break
			}
		}

		// return help or null, if none needed
		return if (shouldHelp) getHelp() else null
	}

	private fun checkInvalid(): String? {
		return null
	}

	fun parse(): String? {
		// check for invalid stuff, or help, or nothing
		return checkInvalid() ?: maybeHelp()
	}

	fun flagNone(index: Int, default: String, help: String): String {
		// add it to the list
		flags["$index"] = Flag(
			flag = "$index",
			shorthand = "$index",
			default = default,
			help = help
		)

		// check if the value is there
		if (args.size >= index) { // if array to small, it will fail instead of returning null
			return args[index]
		} else {
			return default
		}
	}

	fun flagString(flag: String, shorthand: String, default: String, help: String): String {
		// add it to the list
		flags[flag] = Flag(
			flag,
			shorthand,
			default,
			help
		)

		// check if the value is there
		for (i in args.indices) {
			if (args[i] == "--$flag" || args[i] == "-$shorthand") {
				return args[i+1]
			}
		}

		return default
	}

	fun flagBool(flag: String, shorthand: String, help: String): Boolean {
		// add it to the list
		flags[flag] = Flag(
			flag,
			shorthand,
			default = "false",
			help
		)

		// check if the value is there
		for (i in args.indices) {
			if (args[i] == "--$flag" || args[i] == "-$shorthand") {
				return true
			}
		}

		return false
	}

	fun flagBoolSettable(flag: String, shorthand: String, default: Boolean, help: String): Boolean {
		// add it to the list
		flags[flag] = Flag(
			flag,
			shorthand,
			default = "$default",
			help
		)

		// check if the value is there
		for (i in args.indices) {
			if (args[i] == "--$flag" || args[i] == "-$shorthand") {
				return args[i+1].toBoolean()
			}
		}

		return default
	}
}

data class Flag (
	val flag: String,
	val shorthand: String,
	val default: String,
	val help: String
)
