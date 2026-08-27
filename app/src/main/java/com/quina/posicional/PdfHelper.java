package com.quina.posicional;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.os.Build;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.net.Uri;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PdfHelper {
    public static String gerar(Context ctx, QuinaEngine.Resultado r) throws Exception {
        PdfDocument pdf = new PdfDocument();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int w = 595, h = 842;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,1).create());
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);

        p.setColor(Color.rgb(25,118,169)); p.setTextSize(22); p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        c.drawText("QUINA POSICIONAL — RELATÓRIO DO CANDIDATO", 36, 45, p);
        p.setTextSize(10); p.setTypeface(Typeface.DEFAULT); p.setColor(Color.BLACK);
        c.drawText("Verde = selecionada pelo motor | Vermelho = falha / fora do candidato", 36, 65, p);
        c.drawText("Estudo: P01→P05 • Pareto • engrossamento do talo • ciclos • padrões próprios da Quina", 36, 80, p);

        HashSet<Integer> jogo = new HashSet<>(); for(int n:r.jogo) jogo.add(n);
        int startX=42, startY=115, gapX=55, gapY=46, radius=19;
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD)); p.setTextSize(12);
        for(int n=1;n<=80;n++) {
            int idx=n-1, col=idx%10, row=idx/10;
            int x=startX+col*gapX, y=startY+row*gapY;
            p.setColor(jogo.contains(n)? Color.rgb(30,155,75) : Color.rgb(215,58,58));
            c.drawCircle(x,y,radius,p);
            p.setColor(Color.WHITE);
            c.drawText(String.format(Locale.US,"%02d",n), x, y+5, p);
        }
        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD)); p.setTextSize(14); p.setColor(Color.BLACK);
        int y=505;
        c.drawText("RESUMO DO MOTOR", 36, y, p); y+=20;
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(11);
        String[] linhas = r.resumo.split("\\n");
        for(String line: linhas){ if(y>800) break; c.drawText(line,36,y,p); y+=15; }

        pdf.finishPage(page);
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "quina_posicional_resumo_" + ts + ".pdf";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Quina Posicional");
            Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Nao foi possivel criar o PDF em Downloads.");
            OutputStream os = ctx.getContentResolver().openOutputStream(uri);
            pdf.writeTo(os);
            if (os != null) os.close();
            pdf.close();
            return "Downloads/Quina Posicional/" + name;
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Quina Posicional");
            if(!dir.exists()) dir.mkdirs();
            File out = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(out);
            pdf.writeTo(fos);
            fos.close();
            pdf.close();
            return out.getAbsolutePath();
        }
    }
}
