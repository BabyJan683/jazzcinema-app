# Jazz Cinema Pro v8.0 — Build Guide

## What's New in v8.0
- ✅ Supabase Login / Register system
- ✅ User status: Pending (10 movies/day) | Active (unlimited) | Banned | Suspended | Expired
- ✅ 86+ Live TV channels (Sports, News, Drama, Kids, Movies, Docs, Islamic)
- ✅ Full MX Player-style built-in player (gestures, speed, audio, PIP, lock)
- ✅ Settings with 30+ options + Default player (Built-in / MX / VLC)
- ✅ Watch History badge on Downloads tab
- ✅ Movie cache (local Room DB for offline browsing)
- ✅ Categories: Recently Added, Bollywood, South Movies, Hollywood, Pakistani, etc.

---

## 1. Supabase Setup

### Create Table in Supabase Dashboard
Project: **Jazz Cinema**  
Project ID: `kyuvvglwsrewbhlrhhdb`

Run this SQL in Supabase SQL Editor:
```sql
CREATE TABLE IF NOT EXISTS "Jazz_Clients" (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email           TEXT UNIQUE NOT NULL,
  status          TEXT NOT NULL DEFAULT 'Pending',
  active_date     TIMESTAMPTZ,
  expiry_date     TIMESTAMPTZ,
  plan            TEXT DEFAULT 'Free',
  created_at      TIMESTAMPTZ DEFAULT NOW(),
  daily_watch_count INT DEFAULT 0,
  daily_watch_date  DATE DEFAULT CURRENT_DATE
);

-- Enable Row Level Security
ALTER TABLE "Jazz_Clients" ENABLE ROW LEVEL SECURITY;

-- Allow authenticated users to read their own row
CREATE POLICY "users_read_own" ON "Jazz_Clients"
  FOR SELECT USING (auth.uid() = id);

-- Allow authenticated users to insert their own row
CREATE POLICY "users_insert_own" ON "Jazz_Clients"
  FOR INSERT WITH CHECK (auth.uid() = id);

-- Allow authenticated users to update their own row
CREATE POLICY "users_update_own" ON "Jazz_Clients"
  FOR UPDATE USING (auth.uid() = id);
```

### Update Status (Admin)
```sql
-- Activate user
UPDATE "Jazz_Clients" SET status='Active', plan='Premium',
  active_date=NOW(), expiry_date=NOW()+'30 days'
  WHERE email='user@example.com';

-- Ban user
UPDATE "Jazz_Clients" SET status='Banned' WHERE email='user@example.com';

-- Suspend user
UPDATE "Jazz_Clients" SET status='Suspended' WHERE email='user@example.com';
```

---

## 2. Configure Constants.java
Edit: `app/src/main/java/com/cinema/jazz/Constants.java`

```java
// MySQL/JDBC
DB_HOST     = "your-mysql-host.com"
DB_PORT     = 3306
DB_NAME     = "jazz_cinema"
DB_USER     = "jazz_user"
DB_PASSWORD = "your_password"

// WhatsApp (for support button)
WHATSAPP_NUMBER = "923001234567"   // Replace with real number

// Logo URL for splash
LOGO_URL = "https://your-cdn.com/logo.png"
```

Supabase config is in `SupabaseClient.java` (already configured with your project).

---

## 3. Add MySQL JDBC JAR
Download: https://dev.mysql.com/downloads/connector/j/
Place `mysql-connector-java-8.0.33.jar` in `app/libs/`

---

## 4. Build in Android Studio
1. Open project in **Android Studio Hedgehog** or newer
2. File → Sync Project with Gradle Files
3. Build → Generate Signed Bundle/APK → APK
4. Select `release` variant

### Requirements
- Android Studio Hedgehog+
- JDK 17
- Gradle 8.x
- minSdk 26 (Android 8.0)
- compileSdk 34

---

## 5. User Status Flow
| Status    | Can Login | Can Watch | Downloads | Notes |
|-----------|-----------|-----------|-----------|-------|
| Pending   | ✅       | ✅ (10/day)| ✅       | Default for new users |
| Active    | ✅       | ✅ Unlimited| ✅      | Paid plan |
| Banned    | ❌       | ❌        | ❌        | Cannot login at all |
| Suspended | ✅       | ❌        | ❌        | Can login, nothing else |
| Expired   | ✅       | ❌        | ❌        | Subscription ended |

---

## 6. Live Channels
86+ channels pre-loaded in `ChannelsData.java` across 7 categories:
- Sports (4): PAK v BAN, Ten Sports, PTV Sports, Eurosport
- Islamic (4): Saudi Makkah, Madinah, Madani, Paigham TV
- News (30+): Geo, ARY, Dawn, Samaa, BOL, CNN, BBC, Al Jazeera...
- Entertainment (24): ARY Digital, Hum TV, Express, Green, BBC First...
- Kids (4): Cartoon Network, Minimax, Baby TV, CBeebies
- Movies & Music (7): Filmax, Movie One, 8xM, Jalwa, Play TV...
- Docs & Lifestyle (10): Discovery, Animal Planet, BBC Earth...
