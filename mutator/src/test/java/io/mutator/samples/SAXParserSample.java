package io.mutator.samples;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
public class SAXParserSample {
    public static SAXParser create(SAXParserFactory factory) throws Exception {
        return factory.newSAXParser();
    }
}
