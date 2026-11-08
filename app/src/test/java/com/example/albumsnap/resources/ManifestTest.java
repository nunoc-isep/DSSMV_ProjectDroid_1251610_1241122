package com.example.albumsnap.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** O AndroidManifest e o codigo tem de concordar: permissoes, ecras, redirect do Spotify e FileProvider. */
public class ManifestTest {

    private static Document manifest() throws Exception {
        return ResourceFiles.xml(new File(ResourceFiles.main(), "AndroidManifest.xml"));
    }

    private static Set<String> namesOf(Document doc, String tag) {
        Set<String> names = new HashSet<>();
        NodeList nodes = doc.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) names.add(((Element) nodes.item(i)).getAttribute("android:name"));
        return names;
    }

    private static String source(String relativePath) throws Exception {
        return ResourceFiles.text(new File(ResourceFiles.main(), "java/com/example/albumsnap/" + relativePath));
    }

    @Test
    public void declaresTheNeededPermissions() throws Exception {
        Set<String> permissions = namesOf(manifest(), "uses-permission");

        assertTrue(permissions.contains("android.permission.INTERNET"));
        assertTrue(permissions.contains("android.permission.CAMERA"));
    }

    @Test
    public void cameraHardwareIsNotRequired_soTheAppInstallsEverywhere() throws Exception {
        NodeList features = manifest().getElementsByTagName("uses-feature");

        for (int i = 0; i < features.getLength(); i++) {
            Element f = (Element) features.item(i);
            if (f.getAttribute("android:name").equals("android.hardware.camera")) {
                assertEquals("false", f.getAttribute("android:required"));
                return;
            }
        }
        throw new AssertionError("falta o uses-feature da camara");
    }

    @Test
    public void everyScreenOfTheAppIsDeclaredAndHasItsClass() throws Exception {
        Set<String> declared = namesOf(manifest(), "activity");

        for (String name : new String[]{"LoginActivity", "MainMenuActivity", "CameraActivity",
                "AlbumActivity", "LibraryActivity", "SettingsActivity"}) {
            assertTrue("falta declarar " + name, declared.contains(".views." + name));
            assertTrue("falta a classe " + name, new File(ResourceFiles.main(),
                    "java/com/example/albumsnap/views/" + name + ".java").isFile());
        }
    }

    @Test
    public void onlyTheLoginScreenIsExported_andItIsTheLauncher() throws Exception {
        NodeList activities = manifest().getElementsByTagName("activity");

        for (int i = 0; i < activities.getLength(); i++) {
            Element a = (Element) activities.item(i);
            boolean isLogin = a.getAttribute("android:name").equals(".views.LoginActivity");
            assertEquals(a.getAttribute("android:name"), isLogin ? "true" : "false", a.getAttribute("android:exported"));
        }
    }

    @Test
    public void theSpotifyRedirect_matchesTheIntentFilterOfTheLoginScreen() throws Exception {
        Matcher m = Pattern.compile("REDIRECT_URI\\s*=\\s*\"(\\w+)://(\\w+)\"").matcher(source("external/SpotifyAuthManager.java"));
        assertTrue("nao encontro REDIRECT_URI", m.find());
        String scheme = m.group(1);
        String host = m.group(2);

        NodeList data = manifest().getElementsByTagName("data");
        boolean found = false;
        for (int i = 0; i < data.getLength(); i++) {
            Element d = (Element) data.item(i);
            if (scheme.equals(d.getAttribute("android:scheme")) && host.equals(d.getAttribute("android:host"))) found = true;
        }
        assertTrue("o manifest nao recebe " + scheme + "://" + host, found);
        // O handleRedirect compara o mesmo esquema e o mesmo host
        assertTrue(source("external/SpotifyAuthManager.java").contains("\"" + scheme + "\".equals(data.getScheme())"));
        assertTrue(source("external/SpotifyAuthManager.java").contains("\"" + host + "\".equals(data.getHost())"));
    }

    @Test
    public void theLoginScreenKeepsASingleInstance_soTheRedirectReturnsToIt() throws Exception {
        NodeList activities = manifest().getElementsByTagName("activity");

        for (int i = 0; i < activities.getLength(); i++) {
            Element a = (Element) activities.item(i);
            if (a.getAttribute("android:name").equals(".views.LoginActivity")) {
                assertEquals("singleTask", a.getAttribute("android:launchMode"));
                return;
            }
        }
        throw new AssertionError("falta a LoginActivity");
    }

    @Test
    public void theCameraScreenSurvivesRotation_soTheAnalysisIsNotLost() throws Exception {
        NodeList activities = manifest().getElementsByTagName("activity");

        for (int i = 0; i < activities.getLength(); i++) {
            Element a = (Element) activities.item(i);
            if (a.getAttribute("android:name").equals(".views.CameraActivity")) {
                String changes = a.getAttribute("android:configChanges");
                assertTrue(changes, changes.contains("orientation") && changes.contains("screenSize"));
                return;
            }
        }
        throw new AssertionError("falta a CameraActivity");
    }

    @Test
    public void fileProvider_usesTheAuthorityAndPathsTheCameraCodeNeeds() throws Exception {
        NodeList providers = manifest().getElementsByTagName("provider");
        assertEquals(1, providers.getLength());
        Element provider = (Element) providers.item(0);
        assertEquals("${applicationId}.provider", provider.getAttribute("android:authorities"));
        assertEquals("false", provider.getAttribute("android:exported"));
        assertEquals("true", provider.getAttribute("android:grantUriPermissions"));

        // O CameraActivity pede o URI com esta autoridade e guarda as fotos numa pasta que o file_paths expoe
        String camera = source("views/CameraActivity.java");
        assertTrue(camera.contains("getPackageName() + \".provider\""));
        Document paths = ResourceFiles.xml(ResourceFiles.res("xml/file_paths.xml"));
        NodeList cachePaths = paths.getElementsByTagName("cache-path");
        assertEquals(1, cachePaths.getLength());
        String exposed = ((Element) cachePaths.item(0)).getAttribute("path");
        assertTrue(camera.contains("\"" + exposed.replace("/", "") + "\""));
    }
}
