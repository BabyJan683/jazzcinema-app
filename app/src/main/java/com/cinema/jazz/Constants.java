package com.cinema.jazz;

public final class Constants {
    public static final String APP_VERSION      = "8.7.0";
    public static final String KEY_MOVIE_ID     = "movie_id";
    public static final String KEY_TITLE        = "title";
    public static final String KEY_DRIVE_URL    = "drive_url";
    public static final String KEY_PLAY_URL     = "play_url";
    public static final String KEY_THUMB_URL    = "thumb_url";
    public static final String KEY_CATEGORY     = "category";
    public static final String KEY_LANGUAGE     = "language";
    public static final String KEY_YEAR         = "year";

    // ── Main Database (Movies + TikTok Shorts — single unified DB) ────────────
    public static final String DB_HOST         = "sql12.freesqldatabase.com";
    public static final int    DB_PORT         = 3306;
    public static final String DB_NAME         = "sql12824264";
    public static final String DB_USER         = "sql12824264";
    public static final String DB_PASSWORD     = "zx2faqegWz";

    // Unified JDBC URL — used by MovieRepository, ShortsRepository, SplashActivity
    public static final String DB_JDBC_URL =
        "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME
        + "?useSSL=false&connectTimeout=10000&socketTimeout=20000"
        + "&useUnicode=true&characterEncoding=UTF-8";

    // ── Table names ───────────────────────────────────────────────────────────
    public static final String TIKTOK_TABLE = "Tiktok";

    // ── Support ───────────────────────────────────────────────────────────────
    public static final String WHATSAPP_NUMBER  = "923086578490";

    public static final String TMDB_TOKEN =
        "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIyYTlmZWUzMjI4YTA3MWRjMGUwOGI1NWYxYWYzMmY1MCIsIm5iZiI6MTc1ODk3Mzg4MS42MzgsInN1YiI6IjY4ZDdjZmI5OTM2MTI4MzZmNmUxYmNmNiIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.ufc8uwURhqhk2incMuBvGcSxQf2Mru8l1hdA9X3sYP4";
    public static final String TMDB_IMAGE_BASE  = "https://image.tmdb.org/t/p/w200";
    public static final String GEO_NEWS_STREAM  = "https://content.jwplatform.com/manifests/SBpKjFMi.m3u8";
    public static final int    PENDING_DAILY_LIMIT = 10;

    public static final String SPLASH_IMAGE_URL =
        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&q=80";
    public static final String LOGO_URL =
        "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/YouTube_dark_icon_%282017%29.svg/200px-YouTube_dark_icon_%282017%29.svg.png";

    public static final int LOGO_DRAWABLE = R.drawable.ic_jazz_logo;

    // ── Prefs keys ────────────────────────────────────────────────────────────
    public static final String PREF_THEME        = "app_theme";
    public static final String THEME_DEFAULT     = "default";
    public static final String THEME_BLUE_ORANGE = "blue_orange";

    // Data usage prefs
    public static final String PREF_DATA_USED_BYTES = "data_used_bytes";
    public static final String PREF_DATA_PACKAGE_GB = "data_package_gb";
    public static final String PREF_DATA_RESET_DATE = "data_reset_date";

    // Package sizes in GB
    public static final long[] PACKAGE_SIZES_GB = {1, 5, 10, 50, 100};

    // ── Background Sync ───────────────────────────────────────────────────────
    public static final String PREF_LAST_SYNC   = "last_bg_sync";
    public static final long   SYNC_INTERVAL_MS = 15 * 60 * 1000L; // 15 min

    private Constants() {}
}
