package com.quina.posicional;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PdfReport {
    private PdfReport() {}

    public static String create(Context context, AnalysisEngine.Result result) throws Exception {
        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdf.startPage(info);
        Canvas c = page.getCanvas();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setColor(Color.WHITE);
        c.drawRect(0,0,595,842,p);

        p.setColor(Color.rgb(106,27,154));
        c.drawRect(0,0,595,78,p);
        p.setColor(Color.WHITE); p.setTextSize(24); p.setFakeBoldText(true);
        c.drawText("☘  QUINA POSICIONAL", 28, 48, p);

        p.setFakeBoldText(false); p.setTextSize(11); p.setColor(Color.DKGRAY);
        c.drawText("Verde = jogo sugerido | Vermelho = falha", 28, 102, p);

        Set<Integer> selected = new HashSet<>();
        for (int n : result.game) selected.add(n);

        float startX = 40, startY = 140, dx = 64, dy = 48, radius = 18;
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(11); p.setFakeBoldText(true);
        for (int n=1; n<=80; n++) {
            int col = (n-1)%8;
            int row = (n-1)/8;
            float x = startX + col*dx;
            float y = startY + row*dy;
            p.setColor(selected.contains(n) ? Color.rgb(27,143,58) : Color.rgb(212,59,59));
            c.drawCircle(x,y,radius,p);
            p.setColor(Color.WHITE);
            c.drawText(String.format(Locale.US,"%02d",n),x,y+4,p);
        }

        p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(true); p.setColor(Color.BLACK); p.setTextSize(13);
        c.drawText("RESUMO DO MOTOR",28,640,p);
        p.setFakeBoldText(false); p.setTextSize(9.5f);
        float y = 660;
        for (String line : result.summary.split("\\n")) {
            if (y > 820) break;
            c.drawText(line,28,y,p); y += 13;
        }

        pdf.finishPage(page);

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "Quina_Posicional_" + stamp + ".pdf";
        OutputStream out;
        String location;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Quina Posicional");
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Nao foi possivel criar o PDF em Downloads.");
            out = resolver.openOutputStream(uri);
            if (out == null) throw new IllegalStateException("Nao foi possivel abrir o PDF para gravacao.");
            location = "Downloads/Quina Posicional/" + name;
        } else {
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Quina Posicional");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Nao foi possivel criar pasta do PDF.");
            File file = new File(dir, name);
            out = new FileOutputStream(file);
            location = file.getAbsolutePath();
        }

        pdf.writeTo(out);
        out.flush();
        out.close();
        pdf.close();
        return location;
    }
}
