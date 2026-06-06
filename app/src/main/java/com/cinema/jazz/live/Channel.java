package com.cinema.jazz.live;
public class Channel {
    public final String id, name, genre, cat, logoUrl, streamUrl;
    public Channel(String id,String name,String genre,String cat,String logoUrl,String url){
        this.id=id; this.name=name; this.genre=genre; this.cat=cat; this.logoUrl=logoUrl; this.streamUrl=url;
    }
}
