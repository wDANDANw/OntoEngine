package de.benjaminaaron.ontoserver.model;

import com.github.slugify.Slugify;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class Utils {

    final static String DEFAULT_NAMESPACE = "http://onto.de/";
    final static Slugify slugifier = new Slugify().withLowerCase(false);

    public static String ensureUri(String str) {
        // str = full URI or just local name (= word)
        if (isValidUri(str)) {
            return str;
        }
        return DEFAULT_NAMESPACE + slugifier.slugify(str);
    }

    public static boolean isValidUri(String str) {
        try {
            new URL(str).toURI();
            return true;
        } catch (URISyntaxException | MalformedURLException e) {
            return false;
        }
    }

    public static boolean containsOnlyValidUris(Set<String> set) {
        for (String str : set) {
            if (!isValidUri(str)) {
                return false;
            }
        }
        return true;
    }

    public static String pathFromUri(String uri) {
        // TODO doesn't work with multiple "/"s
        return URI.create(uri).getPath().substring(1);
    }

    public static String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    }

    public static File getExportFile(Path exportPath, String baseName, String extension) {
        exportPath.toFile().mkdirs();
        return exportPath.resolve(baseName + "_" + getTimestamp() + "." + extension).toFile();
    }

    public static Object detectLiteralType(String data) {
        if (data.equalsIgnoreCase("true") || data.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(data);
        }
        try {
            return Integer.parseInt(data);
        } catch (NumberFormatException ignored) {}
        try {
            return Double.parseDouble(data);
        } catch (NumberFormatException ignored) {}
        // TODO more types
        return data;
    }

    public static String getValueFromLiteral(Literal literal) {
        // is there a more elegant way to get the value?
        String[] parts = literal.toString().split("http");
        return parts[0].substring(0, parts[0].length() - 2); // -2 for the ^^
    }

    public static String rdfNodeToGraphDatabaseEntryString(RDFNode rdfNode) {
        if (rdfNode.isResource()) {
            return "<" + rdfNode.asResource().getURI() + ">";
        }
        Literal literal = rdfNode.asLiteral();
        if (literal.getDatatype().getJavaClass() == String.class) {
            return "\"" + literal + "\"";
        }
        return "\"" + getValueFromLiteral(literal) + "\"^^<" + literal.getDatatypeURI() + ">";
    }

    public static String setToCompactArrayString(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String entry : set) {
            sb.append(entry).append(",");
        }
        String str = sb.toString();
        return str.substring(0, sb.length() - 1);
    }

    public static String generateRandomId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
