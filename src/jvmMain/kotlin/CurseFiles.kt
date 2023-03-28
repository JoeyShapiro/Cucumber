package com.garden.joeyshapiro

import kotlinx.serialization.Serializable

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