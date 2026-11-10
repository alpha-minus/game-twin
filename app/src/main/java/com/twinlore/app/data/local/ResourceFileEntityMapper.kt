package com.twinlore.app.data.local

import com.twinlore.app.domain.ResourceFile
import com.twinlore.app.domain.ResourceKind
import com.twinlore.app.domain.ResourceSource

fun ResourceFileEntity.toDomain(): ResourceFile = ResourceFile(
    id = id,
    vaultId = vaultId,
    kind = ResourceKind.fromRaw(kind) ?: ResourceKind.GUIDE,
    filename = filename,
    sizeBytes = sizeBytes,
    homeRelativePath = homeRelativePath,
    absolutePath = absolutePath,
    mimeType = mimeType,
    source = ResourceSource.fromRaw(source),
    lastSeenAt = lastSeenAt,
    createdAt = createdAt
)

fun ResourceFile.toEntity(): ResourceFileEntity = ResourceFileEntity(
    id = id,
    vaultId = vaultId,
    kind = kind.raw,
    filename = filename,
    sizeBytes = sizeBytes,
    homeRelativePath = homeRelativePath,
    absolutePath = absolutePath,
    mimeType = mimeType,
    source = source.raw,
    lastSeenAt = lastSeenAt,
    createdAt = createdAt
)
