package com.garden.joeyshapiro

data class Mod (
	val listed: ModListed, // found in Manifest.kt
	val file: ModFile
)

data class ModListed (
	val full: String,
	val link: String,
	val text: String
)