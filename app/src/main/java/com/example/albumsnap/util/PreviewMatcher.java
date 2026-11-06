package com.example.albumsnap.util;

import com.example.albumsnap.model.PreviewQuery;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Decide se uma faixa encontrada noutra fonte (Deezer, iTunes) e a MESMA gravacao da faixa do
 * Spotify: titulo, artista e duracao. Evita previews de versoes diferentes (live, remix, edits).
 * Java puro (sem Android) para poder ser testado facilmente.
 */
public final class PreviewMatcher {

    /** Diferenca maxima de duracao entre as duas versoes (remasters costumam ter a mesma). */
    public static final long DURATION_TOLERANCE_MS = 5000;

    private PreviewMatcher() {
    }

    /** True se o candidato parece ser a mesma gravacao. */
    public static boolean isMatch(PreviewQuery query, String candTitle, String candArtist, long candDurationMs) {
        return score(query, candTitle, candArtist, "", candDurationMs) > 0;
    }

    /**
     * 0 = nao e a mesma faixa; quanto maior, melhor. Exige titulo e artista iguais e, quando as
     * duas duracoes sao conhecidas, que difiram menos de DURATION_TOLERANCE_MS.
     */
    public static int score(PreviewQuery query, String candTitle, String candArtist,
                            String candAlbum, long candDurationMs) {
        int titleScore = titleScore(query.getTitle(), candTitle);
        if (titleScore == 0) return 0;
        if (!artistMatches(query.getArtists(), candArtist)) return 0;

        int score = titleScore;   // 3 = titulo igual, 1 = um contem o outro
        boolean bothDurations = query.getDurationMs() > 0 && candDurationMs > 0;
        // "Home" ~ "Home Sweet Home": um titulo so parecido tem de ter a duracao a confirmar
        if (titleScore == 1 && !bothDurations) return 0;
        if (bothDurations) {
            if (Math.abs(query.getDurationMs() - candDurationMs) > DURATION_TOLERANCE_MS) return 0;
            score += 2;   // duracao confirmada
        }
        String wantedAlbum = normalize(cleanTitle(query.getAlbumTitle()));
        if (!wantedAlbum.isEmpty() && wantedAlbum.equals(normalize(cleanTitle(candAlbum)))) {
            score += 1;   // desempata a favor da mesma edicao do album
        }
        return score;
    }

    private static int titleScore(String wanted, String candidate) {
        String w = normalize(cleanTitle(wanted));
        String c = normalize(cleanTitle(candidate));
        if (w.isEmpty() || c.isEmpty()) return 0;
        if (w.equals(c)) return 3;
        String shorter = w.length() <= c.length() ? w : c;
        String longer = w.length() <= c.length() ? c : w;
        // So palavras inteiras: "home" nao serve para "homeless"
        return shorter.length() >= 4 && containsWords(longer, shorter) ? 1 : 0;
    }

    private static boolean artistMatches(List<String> wantedArtists, String candidateArtist) {
        if (wantedArtists == null || wantedArtists.isEmpty()) return true;   // nada para comparar
        String c = normalize(candidateArtist);
        if (c.isEmpty()) return false;
        for (String wanted : wantedArtists) {
            String w = normalize(wanted);
            if (w.isEmpty()) continue;
            if (w.equals(c)) return true;
            // "The Beatles" ~ "Beatles", "Daft Punk" ~ "Daft Punk feat. Pharrell Williams"
            String shorter = w.length() <= c.length() ? w : c;
            String longer = w.length() <= c.length() ? c : w;
            if (shorter.length() >= 3 && containsWords(longer, shorter)) return true;
        }
        return false;
    }

    private static boolean containsWords(String longer, String shorter) {
        return (" " + longer + " ").contains(" " + shorter + " ");
    }

    /** Remove (feat. ...), [feat. ...], "- Remastered", parenteses e sufixos depois de " - ". */
    public static String cleanTitle(String title) {
        if (title == null) return "";
        String cleaned = title
                .replaceAll("(?i)\\s*\\(feat\\..*?\\)", "")
                .replaceAll("(?i)\\s*\\[feat\\..*?\\]", "")
                .replaceAll("(?i)\\s*-\\s*.*remaster.*$", "")
                .replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "")
                .replaceAll("\\s+-\\s+.*$", "")
                .trim();
        return cleaned.isEmpty() ? title.trim() : cleaned;
    }

    /** Minusculas, sem acentos nem pontuacao, com um espaco entre palavras. */
    public static String normalize(String text) {
        if (text == null) return "";
        String s = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return s.replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
