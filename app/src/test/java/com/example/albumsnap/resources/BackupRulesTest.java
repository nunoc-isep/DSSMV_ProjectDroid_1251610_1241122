package com.example.albumsnap.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Os tokens do Spotify (ficheiro de preferencias "spotify_auth") nao podem ir para o backup na
 * nuvem nem para outro telemovel. Estes testes avisam se alguem remover a regra, mudar o nome
 * do ficheiro ou desligar as regras no manifest.
 */
public class BackupRulesTest {

    private static final String TOKENS_FILE = "spotify_auth.xml";

    private static boolean excludesTokens(Element section) {
        NodeList excludes = section.getElementsByTagName("exclude");
        for (int i = 0; i < excludes.getLength(); i++) {
            Element e = (Element) excludes.item(i);
            if (e.getAttribute("domain").equals("sharedpref") && e.getAttribute("path").equals(TOKENS_FILE)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void autoBackupRules_excludeTheSpotifyTokens() throws Exception {
        Document doc = ResourceFiles.xml(ResourceFiles.res("xml/backup_rules.xml"));

        assertEquals("full-backup-content", doc.getDocumentElement().getTagName());
        assertTrue(excludesTokens(doc.getDocumentElement()));
    }

    @Test
    public void dataExtractionRules_excludeTheTokensFromCloudBackupAndDeviceTransfer() throws Exception {
        Document doc = ResourceFiles.xml(ResourceFiles.res("xml/data_extraction_rules.xml"));

        for (String section : new String[]{"cloud-backup", "device-transfer"}) {
            NodeList nodes = doc.getElementsByTagName(section);
            assertEquals("falta a seccao " + section, 1, nodes.getLength());
            assertTrue("os tokens nao estao excluidos em " + section, excludesTokens((Element) nodes.item(0)));
        }
    }

    @Test
    public void manifest_usesBothRuleFiles() throws Exception {
        String manifest = ResourceFiles.text(new File(ResourceFiles.main(), "AndroidManifest.xml"));

        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""));
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""));
    }

    @Test
    public void theTokenPreferencesFileNameMatchesTheRule() throws Exception {
        File source = new File(ResourceFiles.main(), "java/com/example/albumsnap/external/SpotifyAuthManager.java");

        boolean usesName = Pattern.compile("PREFS\\s*=\\s*\"spotify_auth\"").matcher(ResourceFiles.text(source)).find();

        assertTrue("O nome das preferencias mudou: atualiza tambem as regras de backup", usesName);
    }
}
