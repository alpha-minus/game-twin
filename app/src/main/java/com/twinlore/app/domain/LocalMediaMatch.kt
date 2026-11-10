package com.twinlore.app.domain

data class LocalMediaMatch(
    val resourceKind: ResourceKind,
    val artworkTag: String,              // "cover" | "3dbox" | "fanart" | "miximage" | "marquee" | "screenshot" | "other"
    val absolutePath: String?,
    val contentUriString: String?,
    val filename: String,
    val sizeBytes: Long,
    val extLower: String,
    val searchPathId: String,            // which MediaSearchPath this match came from
    val relativePath: String?,           // path inside the tree, slash-joined (SAF entries only)
)
