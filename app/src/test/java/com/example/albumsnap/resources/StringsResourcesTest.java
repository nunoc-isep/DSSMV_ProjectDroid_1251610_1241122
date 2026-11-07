package com.example.albumsnap.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A mudanca de idioma (EN/PT) depende dos dois strings.xml estarem completos e iguais nos
 * placeholders. Estes testes apanham, sem abrir a app, uma traducao em falta ou um %s trocado.
 */
public class StringsResourcesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sdf]");

    /** nome -> textos (uma string tem 1 texto; um plurals tem um por quantidade). */
    private static Map<String, List<String>> load(String path) throws Exception {
        Map<String, List<String>> out = new TreeMap<>();
        NodeList nodes = ResourceFiles.xml(ResourceFiles.res(path)).getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element)) continue;
            Element e = (Element) nodes.item(i);
            List<String> texts = new ArrayList<>();
            if (e.getTagName().equals("string")) {
                texts.add(e.getTextContent());
            } else if (e.getTagName().equals("plurals")) {
                NodeList items = e.getElementsByTagName("item");
                for (int j = 0; j < items.getLength(); j++) texts.add(items.item(j).getTextContent());
            } else {
                continue;
            }
            out.put(e.getAttribute("name"), texts);
        }
        return out;
    }

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) found.add(m.group());
        Collections.sort(found);
        return found;
    }

    @Test
    public void englishAndPortugueseDefineExactlyTheSameStrings() throws Exception {
        Set<String> en = load("values/strings.xml").keySet();
        Set<String> pt = load("values-pt/strings.xml").keySet();

        Set<String> missingInPt = new TreeSet<>(en);
        missingInPt.removeAll(pt);
        Set<String> missingInEn = new TreeSet<>(pt);
        missingInEn.removeAll(en);

        assertTrue("Faltam em values-pt: " + missingInPt, missingInPt.isEmpty());
        assertTrue("Faltam em values (EN): " + missingInEn, missingInEn.isEmpty());
    }

    @Test
    public void everyTranslationKeepsTheSamePlaceholders() throws Exception {
        Map<String, List<String>> en = load("values/strings.xml");
        Map<String, List<String>> pt = load("values-pt/strings.xml");

        for (String name : en.keySet()) {
            if (!pt.containsKey(name)) continue;   // coberto pelo teste anterior
            assertEquals("numero de textos diferente em " + name, en.get(name).size(), pt.get(name).size());
            for (int i = 0; i < en.get(name).size(); i++) {
                assertEquals("placeholders diferentes em " + name,
                        placeholders(en.get(name).get(i)), placeholders(pt.get(name).get(i)));
            }
        }
    }

    @Test
    public void noStringIsEmptyAndNoApostropheIsUnescaped() throws Exception {
        for (String file : new String[]{"values/strings.xml", "values-pt/strings.xml"}) {
            for (Map.Entry<String, List<String>> e : load(file).entrySet()) {
                for (String text : e.getValue()) {
                    assertFalse(file + ": '" + e.getKey() + "' esta vazia", text.trim().isEmpty());
                    // O aapt rejeita ' sem \ (a menos que o texto todo esteja entre aspas)
                    boolean quoted = text.startsWith("\"") && text.endsWith("\"");
                    assertTrue(file + ": apostrofo sem escape em '" + e.getKey() + "'",
                            quoted || !Pattern.compile("(?<!\\\\)'").matcher(text).find());
                }
            }
        }
    }

    private static Set<String> referencedStrings() throws Exception {
        Set<String> used = new TreeSet<>();
        Pattern inCode = Pattern.compile("R\\.(?:string|plurals)\\.(\\w+)");
        Pattern inXml = Pattern.compile("@(?:string|plurals)/(\\w+)");
        for (File f : ResourceFiles.filesUnder(new File(ResourceFiles.main(), "java"), ".java")) {
            Matcher m = inCode.matcher(ResourceFiles.text(f));
            while (m.find()) used.add(m.group(1));
        }
        List<File> xmlFiles = new ArrayList<>(ResourceFiles.filesUnder(ResourceFiles.res("layout"), ".xml"));
        xmlFiles.addAll(ResourceFiles.filesUnder(ResourceFiles.res("menu"), ".xml"));
        xmlFiles.addAll(ResourceFiles.filesUnder(ResourceFiles.res("xml"), ".xml"));
        xmlFiles.addAll(ResourceFiles.filesUnder(ResourceFiles.res("values"), "themes.xml"));
        xmlFiles.add(new File(ResourceFiles.main(), "AndroidManifest.xml"));
        for (File f : xmlFiles) {
            Matcher m = inXml.matcher(ResourceFiles.text(f));
            while (m.find()) used.add(m.group(1));
        }
        return used;
    }

    @Test
    public void everyStringUsedByTheAppIsDefined() throws Exception {
        Set<String> missing = referencedStrings();
        missing.removeAll(load("values/strings.xml").keySet());

        assertTrue("Strings usadas mas nao definidas: " + missing, missing.isEmpty());
    }

    @Test
    public void noStringIsLeftUnused() throws Exception {
        Set<String> unused = new TreeSet<>(load("values/strings.xml").keySet());
        unused.removeAll(referencedStrings());
        unused.remove("app_name");   // usada pelo sistema (launcher) atraves do manifest

        assertTrue("Strings definidas mas nunca usadas: " + unused, unused.isEmpty());
    }
}
