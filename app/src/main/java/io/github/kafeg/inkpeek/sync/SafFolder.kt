package io.github.kafeg.inkpeek.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** A folder picked in the system's picker (a tree URI): `inkpeek-log/` in it; a file is read again only when it changed. */
class SafFolder(private val r: ContentResolver, private val tree: Uri) : Folder {
    private data class Doc(val id: String, val name: String, val dir: Boolean, val size: Long, val mod: Long)
    private val cache = HashMap<String, Pair<Doc, ByteArray>>()
    private val root = DocumentsContract.getTreeDocumentId(tree)

    private fun uri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun children(id: String): List<Doc> = r.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
        arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED),
        null, null, null)?.use { c -> generateSequence { if (!c.moveToNext()) null else
            Doc(c.getString(0), c.getString(1), c.getString(2) == Document.MIME_TYPE_DIR, c.getLong(3), c.getLong(4)) }.toList() }
        ?: throw java.io.IOException("the folder cannot be read")

    /** Folders found once (a folder gone throws: the app makes a new [SafFolder]). */
    private val dirs = HashMap<String, String>()

    private fun dir(parent: String, name: String, make: Boolean): String? = dirs["$parent/$name"] ?: (children(parent).firstOrNull { it.dir && it.name == name }?.id
        ?: if (!make) null else DocumentsContract.getDocumentId(DocumentsContract.createDocument(r, uri(parent), Document.MIME_TYPE_DIR, name)
            ?: throw java.io.IOException("cannot make $name")))?.also { dirs["$parent/$name"] = it }

    private fun device(d: String, make: Boolean) = dir(root, "inkpeek-log", make)?.let { dir(it, d, make) }

    private fun file(d: String, name: String) = device(d, false)?.let { id -> children(id).firstOrNull { !it.dir && it.name == name } }

    override fun devices() = dir(root, "inkpeek-log", false)?.let { id -> children(id).filter { it.dir }.map { it.name } }.orEmpty()

    override fun files(device: String) = device(device, false)?.let { id -> children(id).filter { !it.dir }.map { it.name } }.orEmpty()

    override fun read(device: String, name: String): ByteArray? {
        val f = file(device, name) ?: return null
        cache[f.id]?.let { (doc, bytes) -> if (doc == f) return bytes }
        val bytes = r.openInputStream(uri(f.id))!!.use { it.readBytes() }
        cache[f.id] = f to bytes
        return bytes
    }

    private fun target(device: String, name: String) = file(device, name)?.id?.let(::uri)
        ?: DocumentsContract.createDocument(r, uri(device(device, true)!!), "application/octet-stream", name) ?: throw java.io.IOException("cannot make $name")

    override fun write(device: String, name: String, bytes: ByteArray) = r.openOutputStream(target(device, name), "wt")!!.use { it.write(bytes) }

    override fun append(device: String, name: String, bytes: ByteArray): Boolean {
        val u = target(device, name)
        val out = runCatching { r.openOutputStream(u, "wa") }.getOrNull() ?: return false
        out.use { it.write(bytes) }
        return true
    }
}
