package com.cinema.jazz.ui.shorts;

public class ShortVideo {
    public final int    id;
    public final String title;
    public final String driveUrl;
    public final String category;
    public final int    releaseYear;

    public ShortVideo(int id, String title, String driveUrl,
                      String category, int releaseYear) {
        this.id          = id;
        this.title       = title;
        this.driveUrl    = driveUrl;
        this.category    = category;
        this.releaseYear = releaseYear;
    }

    /**
     * Returns true if this video is hosted on Jazz Drive (requires JazzDriveResolver).
     * Handles both URL formats:
     *   https://cloud.jazzdrive.com.pk/share-landing/f/TOKEN  (current format)
     *   https://jazzdrive.com.pk/share/f/TOKEN                (legacy format)
     */
    public boolean isJazzShareUrl() {
        if (driveUrl == null) return false;
        return driveUrl.contains("jazzdrive.com.pk/share-landing/f/")
            || driveUrl.contains("jazzdrive.com.pk/share/f/");
    }

    /**
     * Convert Google Drive share link → direct stream URL.
     * Handles:
     *   https://drive.google.com/file/d/FILE_ID/view...
     *   https://drive.google.com/open?id=FILE_ID
     *   https://drive.google.com/uc?id=FILE_ID    (already direct)
     * Falls back to raw URL for non-Drive links.
     * Note: Jazz Drive URLs must go through JazzDriveResolver first — never call streamUrl()
     * on them directly.
     */
    public String streamUrl() {
        if (driveUrl == null || driveUrl.isEmpty()) return "";
        String u = driveUrl.trim();
        if (u.contains("drive.google.com/file/d/")) {
            String fileId = u.replaceAll(".*/file/d/([^/?&]+).*", "$1");
            return "https://drive.google.com/uc?export=download&id=" + fileId;
        }
        if (u.contains("drive.google.com/open") || u.contains("drive.google.com/uc")) {
            String fileId = u.replaceAll(".*[?&]id=([^&]+).*", "$1");
            return "https://drive.google.com/uc?export=download&id=" + fileId;
        }
        return u;
    }
}
