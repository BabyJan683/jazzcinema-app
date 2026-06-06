package com.cinema.jazz.util;

import com.cinema.jazz.model.Movie;
import java.util.*;
import java.util.regex.*;

public class SeriesHelper {

    private static final Pattern EP_PAT = Pattern.compile(
        "^(.+?)(?:\\s+S(\\d+))?\\s+(?:Ep(?:isode)?)\\s*(\\d+)\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    public static String showName(String title) {
        if (title == null) return "";
        Matcher m = EP_PAT.matcher(title.trim());
        return m.matches() ? m.group(1).trim() : title.trim();
    }

    public static int epNumber(String title) {
        if (title == null) return -1;
        Matcher m = EP_PAT.matcher(title.trim());
        if (!m.matches()) return -1;
        try { return Integer.parseInt(m.group(3)); } catch (NumberFormatException e) { return -1; }
    }

    public static int seasonNumber(String title) {
        if (title == null) return 1;
        Matcher m = EP_PAT.matcher(title.trim());
        if (!m.matches() || m.group(2) == null) return 1;
        try { return Integer.parseInt(m.group(2)); } catch (NumberFormatException e) { return 1; }
    }

    public static boolean isEpisode(String title) {
        if (title == null) return false;
        return EP_PAT.matcher(title.trim()).matches();
    }

    public static Map<String, List<Movie>> groupBySeries(List<Movie> movies) {
        Map<String, List<Movie>> map = new LinkedHashMap<>();
        for (Movie m : movies) {
            String name = showName(m.title);
            if (!map.containsKey(name)) map.put(name, new ArrayList<>());
            map.get(name).add(m);
        }
        for (List<Movie> eps : map.values()) {
            eps.sort((a, b) -> {
                int sa = seasonNumber(a.title), sb = seasonNumber(b.title);
                if (sa != sb) return Integer.compare(sa, sb);
                return Integer.compare(epNumber(a.title), epNumber(b.title));
            });
        }
        return map;
    }

    public static List<Movie> representativeList(Map<String, List<Movie>> grouped) {
        List<Movie> out = new ArrayList<>();
        for (Map.Entry<String, List<Movie>> e : grouped.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            Movie first = e.getValue().get(0);
            Movie rep   = new Movie();
            rep.id = first.id; rep.category = first.category;
            rep.thumbnailUrl = first.thumbnailUrl; rep.driveUrl = first.driveUrl;
            rep.playUrl = first.playUrl; rep.releaseYear = first.releaseYear;
            rep.createdAt = first.createdAt;
            if (e.getValue().size() > 1 || isEpisode(first.title)) {
                rep.title = e.getKey(); rep.isSeries = true;
            } else {
                rep.title = first.title; rep.isSeries = false;
            }
            out.add(rep);
        }
        return out;
    }
}
