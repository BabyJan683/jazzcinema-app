package com.cinema.jazz.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;

public class WhatsAppHelper {

    /** Shows subscribe dialog with JazzCash + Faizan Mobile Shop options. */
    public static void showSubscribeDialog(Context ctx) {
        new AlertDialog.Builder(ctx, R.style.DarkDialog)
            .setTitle("📦 Package Subscribe Karen")
            .setMessage(
                "Package lene ke liye aap 2 tarike se subscribe kar sakte hain:\n\n" +
                "💳 JazzCash se subscribe karen\n\n" +
                "🏪 Faizan Mobile Shop se lagwayen\n\n" +
                "Packages:\n" +
                "• 1 GB  — 1 Month\n" +
                "• 5 GB  — 1 Month\n" +
                "• 10 GB — 1 Month\n" +
                "• 50 GB — 1 Month\n" +
                "• 100 GB — 1 Month\n\n" +
                "WhatsApp karen: +923086578490"
            )
            .setPositiveButton("WhatsApp Karen ▶", (d, w) -> {
                try {
                    String text = Uri.encode(
                        "Assalam o Alaikum! Main Jazz Cinema ka package subscribe karna chahta hoon. " +
                        "Mujhe package ki details chahiye."
                    );
                    ctx.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://api.whatsapp.com/send?phone=" +
                            Constants.WHATSAPP_NUMBER + "&text=" + text)));
                } catch (Exception ignored) {
                    // fallback: open phone dialer
                    try {
                        ctx.startActivity(new Intent(Intent.ACTION_DIAL,
                            Uri.parse("tel:+" + Constants.WHATSAPP_NUMBER)));
                    } catch (Exception ex2) { /* ignore */ }
                }
            })
            .setNegativeButton("Bad Mein", null)
            .show();
    }

    /** Direct WhatsApp open without dialog. */
    public static void openWhatsApp(Context ctx) {
        try {
            String text = Uri.encode(
                "Assalam o Alaikum! Jazz Cinema app ke baare mein information chahiye."
            );
            ctx.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://api.whatsapp.com/send?phone=" +
                    Constants.WHATSAPP_NUMBER + "&text=" + text)));
        } catch (Exception e) {
            try {
                ctx.startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:+" + Constants.WHATSAPP_NUMBER)));
            } catch (Exception ex) { /* ignore */ }
        }
    }
}
