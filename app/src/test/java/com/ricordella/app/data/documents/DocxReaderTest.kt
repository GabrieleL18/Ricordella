package com.ricordella.app.data.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxReaderTest {

    private fun docx(body: String): ByteArray {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""
        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml")); zip.write("<Types/>".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("word/document.xml")); zip.write(xml.toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
    }

    @Test
    fun readsHeadingsBoldAndBullets() {
        val paragraphs = DocxReader.read(
            ByteArrayInputStream(
                docx(
                    """
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Contratto</w:t></w:r></w:p>
                    <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Scadenza:</w:t></w:r><w:r><w:t xml:space="preserve"> 31/12</w:t></w:r></w:p>
                    <w:p><w:pPr><w:numPr/></w:pPr><w:r><w:t>Rinnovo automatico</w:t></w:r></w:p>
                    """,
                ),
            ),
        )
        assertEquals(3, paragraphs.size)
        assertEquals(1, paragraphs[0].headingLevel)
        assertEquals("Scadenza: 31/12", paragraphs[1].text)
        assertTrue(paragraphs[1].runs.first().bold)
        assertTrue(paragraphs[2].bullet)
    }

    @Test
    fun notADocxGivesNothing() {
        assertEquals(emptyList<DocParagraph>(), DocxReader.read(ByteArrayInputStream(docx("").let { ByteArray(0) })))
    }
}
