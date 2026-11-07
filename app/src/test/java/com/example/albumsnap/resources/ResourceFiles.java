package com.example.albumsnap.resources;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Acesso aos ficheiros do projeto (res, manifest, codigo) para os testes que os verificam. */
final class ResourceFiles {

    private ResourceFiles() {
    }

    /** Pasta src/main: o Gradle corre os testes na pasta "app"; o Android Studio pode correr na raiz. */
    static File main() {
        File fromModule = new File("src/main");
        if (fromModule.isDirectory()) return fromModule;
        File fromRoot = new File("app/src/main");
        if (fromRoot.isDirectory()) return fromRoot;
        throw new IllegalStateException("Nao encontro src/main a partir de " + new File(".").getAbsolutePath());
    }

    static File res(String relativePath) {
        return new File(main(), "res/" + relativePath);
    }

    static Document xml(File file) throws IOException, SAXException, ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
    }

    static String text(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Todos os ficheiros com esta extensao dentro de uma pasta (recursivo). */
    static List<File> filesUnder(File dir, String extension) throws IOException {
        try (Stream<java.nio.file.Path> paths = Files.walk(dir.toPath())) {
            return paths.map(java.nio.file.Path::toFile)
                    .filter(f -> f.isFile() && f.getName().endsWith(extension))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }
}
