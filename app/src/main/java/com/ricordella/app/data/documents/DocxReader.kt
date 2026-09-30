package com.ricordella.app.data.documents

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Un paragrafo di un documento Word: pezzi di testo con grassetto/corsivo, livello di titolo, elenco. */
data class DocParagraph(val runs: List<DocRun>, val headingLevel: Int = 0, val bullet: Boolean = false) {
    val text: String get() = runs.joinToString("") { it.text }
}

data class DocRun(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/**
 * Lettore minimale di file .docx: il documento è uno zip con il testo in word/document.xml.
 * Estrae paragrafi, titoli, elenchi, grassetto e corsivo, e il testo delle tabelle (riga per riga).
 * ponytail: immagini, colori e impaginazione non sono resi; per quelli c'è "Apri con…".
 */
object DocxReader {

    fun read(input: InputStream): List<DocParagraph> {
        val xml = ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "word/document.xml" } ?: return emptyList()
            zip.readBytes()
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Nessuna entità esterna: il file arriva dall'utente.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val document = factory.newDocumentBuilder().parse(xml.inputStream())
        val paragraphs = mutableListOf<DocParagraph>()
        val nodes = document.getElementsByTagNameNS(W, "p")
        for (i in 0 until nodes.length) {
            val p = nodes.item(i) as Element
            val props = p.child("pPr")
            val style = props?.child("pStyle")?.getAttributeNS(W, "val").orEmpty().lowercase()
            val heading = Regex("""(heading|titolo|title)\s*(\d?)""").find(style)?.let { match ->
                match.groupValues[2].toIntOrNull() ?: 1
            } ?: 0
            val bullet = props?.child("numPr") != null
            val runs = mutableListOf<DocRun>()
            p.descendants("r").forEach { r ->
                val rPr = r.child("rPr")
                val bold = rPr?.child("b") != null
                val italic = rPr?.child("i") != null
                val text = buildString {
                    r.childNodes.forEachElement { node ->
                        when (node.localName) {
                            "t" -> append(node.textContent)
                            "tab" -> append('\t')
                            "br", "cr" -> append('\n')
                        }
                    }
                }
                if (text.isNotEmpty()) runs += DocRun(text, bold, italic)
            }
            paragraphs += DocParagraph(runs, heading, bullet)
        }
        // Più righe vuote di fila diventano una sola.
        return paragraphs.filterIndexed { index, paragraph ->
            paragraph.text.isNotBlank() || (index > 0 && paragraphs[index - 1].text.isNotBlank())
        }
    }

    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    private fun Element.child(name: String): Element? {
        var node = firstChild
        while (node != null) {
            if (node is Element && node.localName == name) return node
            node = node.nextSibling
        }
        return null
    }

    /** Run del paragrafo, anche dentro collegamenti ipertestuali, ma non nei paragrafi annidati. */
    private fun Element.descendants(name: String): List<Element> {
        val result = mutableListOf<Element>()
        fun visit(node: Node) {
            node.childNodes.forEachElement { child ->
                when (child.localName) {
                    name -> result += child
                    "p" -> Unit
                    else -> visit(child)
                }
            }
        }
        visit(this)
        return result
    }

    private inline fun org.w3c.dom.NodeList.forEachElement(action: (Element) -> Unit) {
        for (i in 0 until length) (item(i) as? Element)?.let(action)
    }
}
