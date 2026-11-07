package net.digitalbc.leafreader.data.jellyfin

import java.io.IOException

class JellyfinException(
    val kind: JellyfinFailureKind,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
