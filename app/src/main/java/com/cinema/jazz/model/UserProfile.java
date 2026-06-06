package com.cinema.jazz.model;

import android.content.Context;
import android.content.SharedPreferences;

public class UserProfile {
    public String id, email, status, plan, activeDate, expiryDate, createdAt, accessToken;
    public int    dailyWatchCount;
    public String dailyWatchDate;
    // Data package fields
    public long   dataPackageGb;   // e.g. 1, 5, 10, 50, 100  (0 = no package / free)
    public long   dataUsedBytes;   // bytes used this billing period
    public String dataResetDate;   // ISO date when current billing period started

    public boolean isActive()    { return "Active".equalsIgnoreCase(status); }
    public boolean isBanned()    { return "Banned".equalsIgnoreCase(status); }
    public boolean isSuspended() { return "Suspended".equalsIgnoreCase(status); }
    public boolean isPending()   { return "Pending".equalsIgnoreCase(status); }
    public boolean isExpired()   { return "Expired".equalsIgnoreCase(status); }
    public boolean canWatch()    { return !isBanned() && !isSuspended() && !isExpired(); }
    public boolean isUnlimited() { return isActive(); }
    public boolean hasDataPackage() { return dataPackageGb > 0; }

    public void save(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("jazz_user", Context.MODE_PRIVATE);
        p.edit()
            .putString("id", id).putString("email", email)
            .putString("status", status).putString("plan", plan)
            .putString("activeDate", activeDate).putString("expiryDate", expiryDate)
            .putString("accessToken", accessToken)
            .putInt("dailyWatchCount", dailyWatchCount)
            .putString("dailyWatchDate", dailyWatchDate)
            .putLong("dataPackageGb", dataPackageGb)
            .putLong("dataUsedBytes", dataUsedBytes)
            .putString("dataResetDate", dataResetDate != null ? dataResetDate : "")
            .apply();
    }

    public static UserProfile load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("jazz_user", Context.MODE_PRIVATE);
        if (!p.contains("id")) return null;
        UserProfile u = new UserProfile();
        u.id              = p.getString("id", "");
        u.email           = p.getString("email", "");
        u.status          = p.getString("status", "Pending");
        u.plan            = p.getString("plan", "Free");
        u.activeDate      = p.getString("activeDate", "");
        u.expiryDate      = p.getString("expiryDate", "");
        u.accessToken     = p.getString("accessToken", "");
        u.dailyWatchCount = p.getInt("dailyWatchCount", 0);
        u.dailyWatchDate  = p.getString("dailyWatchDate", "");
        u.dataPackageGb   = p.getLong("dataPackageGb", 0L);
        u.dataUsedBytes   = p.getLong("dataUsedBytes", 0L);
        u.dataResetDate   = p.getString("dataResetDate", "");
        return u;
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences("jazz_user", Context.MODE_PRIVATE).edit().clear().apply();
    }
}
