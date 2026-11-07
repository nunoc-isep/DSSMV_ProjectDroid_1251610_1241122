package com.example.albumsnap.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ligacao entre o codigo e os layouts: ids e recursos que o codigo usa tem de existir. */
public class LayoutsTest {

    private static Set<String> definedIds() throws Exception {
        Set<String> ids = new TreeSet<>();
        Pattern def = Pattern.compile("@\\+id/(\\w+)");
        for (File f : ResourceFiles.filesUnder(ResourceFiles.res(""), ".xml")) {
            Matcher m = def.matcher(ResourceFiles.text(f));
            while (m.find()) ids.add(m.group(1));
        }
        return ids;
    }

    @Test
    public void everyIdUsedInJavaIsDefinedInSomeLayoutOrMenu() throws Exception {
        Set<String> used = new TreeSet<>();
        Pattern inCode = Pattern.compile("R\\.id\\.(\\w+)");
        for (File f : ResourceFiles.filesUnder(new File(ResourceFiles.main(), "java"), ".java")) {
            Matcher m = inCode.matcher(ResourceFiles.text(f));
            while (m.find()) used.add(m.group(1));
        }
        used.removeAll(definedIds());

        assertTrue("ids usados no codigo mas inexistentes: " + used, used.isEmpty());
    }

    @Test
    public void everyLayoutMenuAndDrawableUsedInJavaExists() throws Exception {
        Set<String> missing = new TreeSet<>();
        Pattern inCode = Pattern.compile("R\\.(layout|menu|drawable)\\.(\\w+)");
        for (File f : ResourceFiles.filesUnder(new File(ResourceFiles.main(), "java"), ".java")) {
            Matcher m = inCode.matcher(ResourceFiles.text(f));
            while (m.find()) {
                boolean found = false;
                File dir = ResourceFiles.res(m.group(1));
                File[] candidates = dir.listFiles();
                if (candidates != null) {
                    for (File c : candidates) {
                        if (c.getName().startsWith(m.group(2) + ".")) found = true;
                    }
                }
                if (!found) missing.add(m.group(1) + "/" + m.group(2));
            }
        }
        assertTrue("recursos usados no codigo mas inexistentes: " + missing, missing.isEmpty());
    }

    @Test
    public void trackRow_hasThePreviewAndTheThreeDotMenuButtons() throws Exception {
        String layout = ResourceFiles.text(ResourceFiles.res("layout/item_track.xml"));

        assertTrue(layout.contains("@+id/btnPreview"));
        assertTrue(layout.contains("@+id/btnTrackMenu"));
    }

    @Test
    public void trackMenu_offersFavouriteAndPlaylist() throws Exception {
        Document menu = ResourceFiles.xml(ResourceFiles.res("menu/menu_track.xml"));
        NodeList items = menu.getElementsByTagName("item");
        Set<String> ids = new TreeSet<>();
        for (int i = 0; i < items.getLength(); i++) {
            ids.add(((Element) items.item(i)).getAttribute("android:id"));
        }

        assertEquals(2, items.getLength());
        assertTrue(ids.contains("@+id/action_track_favourite"));
        assertTrue(ids.contains("@+id/action_track_playlist"));
    }

    @Test
    public void everyLayoutIsWellFormedXml() throws Exception {
        for (File f : ResourceFiles.filesUnder(ResourceFiles.res("layout"), ".xml")) {
            ResourceFiles.xml(f);   // rebenta se o XML estiver mal formado
        }
    }
}
