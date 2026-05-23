package com.multibot.indexing.infrastructure.tika;

import com.multibot.indexing.domain.model.ExtractedText;
import com.multibot.indexing.domain.port.out.TextExtractor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

@Component
class TikaTextExtractorAdapter implements TextExtractor {

    private static final int MAX_TEXT_LENGTH = -1;

    private final Tika tika;
    private final AutoDetectParser parser;

    TikaTextExtractorAdapter() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
    }

    @Override
    public ExtractedText extract(byte[] bytes, String declaredMimeType, String filename) {
        String detectedMimeType = tika.detect(bytes, filename);

        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

            parser.parse(input, handler, metadata, new ParseContext());

            String content = handler.toString().trim();
            String language = metadata.get(TikaCoreProperties.LANGUAGE);

            return new ExtractedText(content, Optional.ofNullable(language), detectedMimeType);
        } catch (IOException | TikaException | org.xml.sax.SAXException e) {
            throw new IllegalStateException("Failed to extract text via Tika: " + e.getMessage(), e);
        }
    }
}
