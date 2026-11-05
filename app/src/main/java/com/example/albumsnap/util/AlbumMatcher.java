package com.example.albumsnap.util;

import com.example.albumsnap.model.Album;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ordena os albuns encontrados pelo Spotify conforme o quanto se parecem com o que foi lido
 * na capa (titulo e artista), e decide se ha um vencedor claro. Java puro.
 */
public final class AlbumMatcher {

    /**
     * O album so e aberto sem mostrar o menu de escolha quando e quase uma correspondencia exata
     * e destacada dos restantes. Em qualquer outro caso o utilizador escolhe na lista.
     */
    public static final double CONFIDENT_SCORE = 0.95;
    /** Diferenca minima de pontuacao para o segundo candidato. */
    public static final double CONFIDENT_GAP = 0.30;

    private static final double QUERY_WEIGHT_STEP = 0.08;
    private static final double MIN_QUERY_WEIGHT = 0.6;

    private static final Set<String> IGNORED = new HashSet<>(Arrays.asList(
            "the", "a", "an", "of", "and", "feat", "ft", "deluxe", "edition", "remastered",
            "remaster", "version", "expanded", "anniversary", "special", "bonus"));

    private AlbumMatcher() {
    }

    /** Albuns por ordem de semelhanca (empates mantem a ordem original do Spotify). */
    public static List<Album> rank(List<Album> albums, List<String> queries) {
        final List<Album> copy = new ArrayList<>(albums);
        final java.util.Map<Album, Double> scores = new java.util.IdentityHashMap<>();
        for (Album a : copy) scores.put(a, score(a, queries));
        Collections.sort(copy, new Comparator<Album>() {
            @Override
            public int compare(Album x, Album y) {
                return Double.compare(scores.get(y), scores.get(x));   // sort estavel
            }
        });
        return copy;
    }

    /** True se o primeiro da lista (ja ordenada) se destaca claramente. */
    public static boolean isConfident(List<Album> ranked, List<String> queries) {
        if (ranked.isEmpty()) return false;
        double top = score(ranked.get(0), queries);
        if (top < CONFIDENT_SCORE) return false;
        if (ranked.size() == 1) return true;   // so um candidato em todas as pesquisas
        return top - score(ranked.get(1), queries) >= CONFIDENT_GAP;
    }

    /** 0..1: melhor pontuacao do album contra qualquer uma das pesquisas. */
    public static double score(Album album, List<String> queries) {
        Set<String> title = tokens(album.getTitle());
        Set<String> artist = tokens(album.getArtistNames());
        if (title.isEmpty()) return 0;

        double best = 0;
        for (int i = 0; i < queries.size(); i++) {
            Set<String> query = tokens(queries.get(i));
            if (query.isEmpty()) continue;

            // As pesquisas vem da mais para a menos fiavel: as ultimas valem um pouco menos
            double weight = Math.max(MIN_QUERY_WEIGHT, 1.0 - QUERY_WEIGHT_STEP * i);

            double titleCoverage = overlap(title, query) / title.size();
            double artistCoverage = artist.isEmpty() ? 0 : overlap(artist, query) / artist.size();

            Set<String> both = new HashSet<>(title);
            both.addAll(artist);
            double queryCoverage = overlap(both, query) / query.size();

            best = Math.max(best, weight
                    * (0.5 * titleCoverage + 0.2 * artistCoverage + 0.3 * queryCoverage));
        }
        return best;
    }

    private static double overlap(Set<String> a, Set<String> b) {
        int n = 0;
        for (String t : a) if (b.contains(t)) n++;
        return n;
    }

    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        String s = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (s.isEmpty()) return out;
        for (String t : s.split(" ")) {
            if (!t.isEmpty() && !IGNORED.contains(t)) out.add(t);
        }
        return out;
    }
}
