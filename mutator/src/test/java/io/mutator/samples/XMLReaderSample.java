package io.mutator.samples;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
public class XMLReaderSample {
    public static void parse(XMLReader reader, InputSource source) throws Exception {
        reader.parse(source);
    }
}
