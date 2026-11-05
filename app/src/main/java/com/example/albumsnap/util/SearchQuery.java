package com.example.albumsnap.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Limpeza do texto que vem do reconhecimento de imagem antes de pesquisar no Spotify.
 * Java puro (sem Android) para poder ser testado facilmente.
 */
public final class SearchQuery {

    // Expressoes que o Google acrescenta aos rotulos ("daft punk ... album cover") e ruido
    // impresso nas capas. Sao removidas do texto, em vez de se rejeitar o rotulo inteiro.
    private static final Pattern NOISE = Pattern.compile(
            "\\b(album covers?|cover art|album art(?:work)?|official|parental advisory"
                    + "|explicit content|explicit|advisory|stereo)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Rotulos que, sozinhos (depois de limpos), nao dizem nada sobre o album.
    // So se comparam com o rotulo INTEIRO: "Art Blakey" ou "Earth, Wind & Fire" nao sao afetados.
    private static final Pattern GENERIC = Pattern.compile(
            "(arts?|artworks?|visual arts?|illustrations?|posters?|fonts?|pictures?|images?"
                    + "|graphic design|design|text|logo|brand|line|circle|rectangle|product"
                    + "|advertising|soundtracks?|compilations?|music|cd|vinyl|records?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Link de um album no Spotify: open.spotify.com/album/<22 caracteres>, com ou sem "intl-xx/"
    private static final Pattern SPOTIFY_ALBUM_URL = Pattern.compile(
            "open\\.spotify\\.com/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?album/([0-9A-Za-z]{22})",
            Pattern.CASE_INSENSITIVE);

    // Sites de musica cujas paginas costumam ter o nome do album no titulo
    private static final String[] MUSIC_SITES = {
            "spotify.com", "music.apple.com", "itunes.apple.com", "discogs.com", "wikipedia.org",
            "bandcamp.com", "deezer.com", "genius.com", "last.fm", "rateyourmusic.com",
            "musicbrainz.org", "allmusic.com", "tidal.com", "music.amazon", "music.youtube.com",
            "qobuz.com", "shazam.com"};

    // Nomes de sites e expressoes que aparecem nos titulos das paginas
    private static final Pattern PAGE_TITLE_NOISE = Pattern.compile(
            "\\b(album by|on spotify|on apple music|on deezer|on tidal|on bandcamp|spotify|apple music"
                    + "|itunes|discogs|wikipedia|bandcamp|deezer|genius|last\\.fm|rate your music"
                    + "|musicbrainz|allmusic|tidal|amazon music|youtube music|qobuz|shazam|releases"
                    + "|discography|lyrics|tracklist|listen|stream)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Imagem de capa do Spotify: i.scdn.co/image/<16 de prefixo (tamanho)><24 que identificam a imagem>.
    // O mesmo ficheiro aparece com prefixos diferentes por tamanho, por isso so se compara o fim.
    private static final Pattern SPOTIFY_COVER = Pattern.compile(
            "i\\.scdn\\.co/image/[0-9a-f]{16}([0-9a-f]{24})", Pattern.CASE_INSENSITIVE);

    private SearchQuery() {
    }

    /** Parte do URL de uma capa do Spotify que identifica a imagem (null se nao for uma capa do Spotify). */
    public static String spotifyCoverKey(String url) {
        if (url == null) return null;
        Matcher m = SPOTIFY_COVER.matcher(url);
        return m.find() ? m.group(1).toLowerCase() : null;
    }

    /**
     * True se o texto (ja limpo) parece texto real e nao ruido do OCR (capas sem letras
     * costumam dar "I", "AA", "l1" ...): pede uma palavra com 3+ caracteres ou duas com 2+.
     */
    public static boolean isMeaningfulText(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) return false;
        int longWords = 0;
        int mediumWords = 0;
        for (String w : cleaned.split("\\s+")) {
            if (w.length() >= 3) longWords++;
            if (w.length() >= 2) mediumWords++;
        }
        return longWords >= 1 || mediumWords >= 2;
    }

    /** Id do album se o URL for uma pagina de album do Spotify; senao null. */
    public static String spotifyAlbumId(String url) {
        if (url == null) return null;
        Matcher m = SPOTIFY_ALBUM_URL.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** True se o URL pertence a um site de musica conhecido. */
    public static boolean isMusicSite(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        for (String site : MUSIC_SITES) {
            if (u.contains(site)) return true;
        }
        return false;
    }

    /**
     * Transforma o titulo de uma pagina ("Random Access Memories - Album by Daft Punk | Spotify")
     * numa pesquisa ("Random Access Memories Daft Punk"). Devolve "" se nao sobrar nada util.
     */
    public static String fromPageTitle(String title) {
        if (title == null) return "";
        String s = title;
        int pipe = s.indexOf('|');
        if (pipe > 0) s = s.substring(0, pipe);               // "... | Spotify"
        s = s.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " ");  // "(2013, CD)", "[Deluxe]"
        s = PAGE_TITLE_NOISE.matcher(s).replaceAll(" ");
        return clean(s);
    }

    /** Remove ruido e pontuacao, junta espacos. Devolve "" se nao sobrar nada util. */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replace('’', '\'').replace('‘', '\'');
        s = NOISE.matcher(s).replaceAll(" ");
        s = s.replaceAll("[^\\p{L}\\p{N}' ]+", " ");   // quebras de linha e pontuacao -> espaco
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /** True se o texto (ja limpo) esta vazio ou e apenas um termo generico. */
    public static boolean isGeneric(String cleaned) {
        return cleaned == null || cleaned.isEmpty() || GENERIC.matcher(cleaned).matches();
    }

    /** Primeiras n palavras. */
    public static String firstWords(String text, int n) {
        if (text == null) return "";
        String[] words = text.trim().split("\\s+");
        if (words.length <= n) return text.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    public static int wordCount(String text) {
        return text == null || text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
    }
}
