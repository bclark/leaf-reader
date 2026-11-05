package net.digitalbc.leafreader.data.smb

import net.digitalbc.leafreader.data.archive.NaturalOrderComparator

object SmbPaths {
    @Throws(IllegalArgumentException::class)
    fun normalize(path: String): String {
        require(path.none { it == '\u0000' || it == ':' }) { "Invalid SMB path" }
        val unix = path.replace('\\', '/')
        require(!unix.startsWith('/')) { "SMB path must be relative" }
        val parts = unix.split('/').filter { it.isNotEmpty() && it != "." }
        // Windows SMB servers can trim trailing dots/spaces before resolving dot segments.
        require(parts.none { it.trimEnd(' ', '.').isEmpty() }) { "SMB path cannot escape the configured directory" }
        return parts.joinToString("/")
    }

    fun isChildName(name: String): Boolean =
        name.trimEnd(' ', '.').isNotEmpty() && name.none { it in "/\\:\u0000" }

    @Throws(IllegalArgumentException::class)
    fun child(parent: String, name: String): String {
        require(isChildName(name)) { "Invalid SMB entry name" }
        val base = normalize(parent)
        return if (base.isEmpty()) name else "$base/$name"
    }

    @Throws(IllegalArgumentException::class)
    fun parent(path: String): String = normalize(path).substringBeforeLast('/', "")

    @Throws(IllegalArgumentException::class)
    fun join(base: String, relative: String): String =
        listOf(normalize(base), normalize(relative)).filter { it.isNotEmpty() }.joinToString("/")
}

object SmbListing {
    fun isImportable(name: String): Boolean =
        SmbPaths.isChildName(name) && !name.startsWith('.') &&
            name.substringAfterLast('.', "").lowercase() in setOf("cbz", "cbr", "pdf", "zip", "rar")

    /** Reject malformed/foreign paths before displaying or selecting a server-supplied entry. */
    fun prepare(directory: String, entries: List<SmbEntry>): List<SmbEntry> {
        val base = SmbPaths.normalize(directory)
        return entries.filter {
            SmbPaths.isChildName(it.name) && !it.name.startsWith('.') &&
                (it.isDirectory || isImportable(it.name)) && it.path == SmbPaths.child(base, it.name)
        }.distinctBy { it.path }.sortedWith { a, b ->
            when {
                a.isDirectory != b.isDirectory -> if (a.isDirectory) -1 else 1
                else -> NaturalOrderComparator.compare(a.name, b.name).takeIf { it != 0 }
                    ?: a.path.compareTo(b.path)
            }
        }
    }
}
