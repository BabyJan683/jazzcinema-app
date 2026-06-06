package com.cinema.jazz.model;
public class Movie {
    public int    id;
    public String title       = "";
    public String category    = "";
    public String thumbnailUrl = "";
    public String driveUrl    = "";
    public String playUrl     = "";
    public int    releaseYear;
    public String createdAt   = "";
    /** True when this entry is a grouped series representative (set by SeriesHelper). */
    public boolean isSeries   = false;
}
