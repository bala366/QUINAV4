package com.quina.posicional;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int OPEN_HISTORY = 1001;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Button importButton;
    private Button analyzeButton;
    private Button pdfButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView historyText;
    private TextView resultText;

    private List<int[]> history = new ArrayList<>();
    private AnalysisEngine.Result lastResult;
    private volatile boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(buildUi());
            setStatus(0, "Pronto. Importe o historico TXT para comecar.");
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(16), dp(18), dp(16), dp(18));
        header.setBackgroundColor(Color.rgb(106,27,154));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("☘  QUINA POSICIONAL");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("P01 → P05  •  Pareto  •  Talo sem reset");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        header.addView(subtitle);

        TextView info = new TextView(this);
        info.setText("História inteira • dois últimos ciclos • padrões próprios da Quina • progresso real • PDF verde/vermelho");
        info.setTextSize(16);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(dp(4), dp(18), dp(4), dp(14));
        root.addView(info);

        importButton = button("IMPORTAR HISTÓRICO TXT");
        root.addView(importButton, buttonParams());
        importButton.setOnClickListener(v -> openHistoryPicker());

        historyText = new TextView(this);
        historyText.setText("Nenhum histórico carregado.");
        historyText.setTextColor(Color.DKGRAY);
        historyText.setTextSize(15);
        historyText.setPadding(dp(4), dp(8), dp(4), dp(12));
        root.addView(historyText);

        analyzeButton = button("ANALISAR PRÓXIMA TENDÊNCIA");
        analyzeButton.setEnabled(false);
        root.addView(analyzeButton, buttonParams());
        analyzeButton.setOnClickListener(v -> analyze());

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(12));
        pp.setMargins(0, dp(16), 0, dp(8));
        root.addView(progressBar, pp);

        statusText = new TextView(this);
        statusText.setTextSize(16);
        statusText.setTextColor(Color.DKGRAY);
        statusText.setPadding(dp(4), dp(4), dp(4), dp(12));
        root.addView(statusText);

        resultText = new TextView(this);
        resultText.setTextSize(16);
        resultText.setTextColor(Color.rgb(33,33,33));
        resultText.setTextIsSelectable(true);
        resultText.setPadding(dp(4), dp(8), dp(4), dp(16));
        root.addView(resultText);

        pdfButton = button("GERAR PDF RESUMO");
        pdfButton.setEnabled(false);
        root.addView(pdfButton, buttonParams());
        pdfButton.setOnClickListener(v -> generatePdf());

        return scroll;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(106,27,154));
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.setMargins(0, dp(6), 0, dp(6));
        return p;
    }

    private void openHistoryPicker() {
        if (running) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        startActivityForResult(i, OPEN_HISTORY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_HISTORY && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            loadHistory(uri);
        }
    }

    private void loadHistory(Uri uri) {
        if (running) return;
        running = true;
        setControls(false);
        setStatus(5, "Lendo e validando o arquivo selecionado...");

        executor.execute(() -> {
            try {
                List<int[]> loaded = parseHistory(uri);
                if (loaded.size() < 30) throw new IllegalArgumentException("Arquivo possui apenas " + loaded.size() + " concursos validos.");
                history = loaded;
                lastResult = null;
                main.post(() -> {
                    historyText.setText("Histórico carregado: " + loaded.size() + " concursos válidos.\nÚltimo resultado: " + formatGame(loaded.get(loaded.size()-1)));
                    resultText.setText("");
                    setStatus(100, "Histórico pronto. Toque em ANALISAR PRÓXIMA TENDÊNCIA.");
                    running = false;
                    importButton.setEnabled(true);
                    analyzeButton.setEnabled(true);
                    pdfButton.setEnabled(false);
                });
            } catch (Throwable t) {
                main.post(() -> {
                    running = false;
                    setControls(true);
                    analyzeButton.setEnabled(!history.isEmpty());
                    pdfButton.setEnabled(lastResult != null);
                    setStatus(0, "Erro ao importar: " + safeMessage(t));
                    Toast.makeText(this, "Não consegui ler esse arquivo.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private List<int[]> parseHistory(Uri uri) throws Exception {
        List<int[]> out = new ArrayList<>();
        Pattern number = Pattern.compile("\\d+");
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                Matcher m = number.matcher(line);
                List<Integer> vals = new ArrayList<>();
                while (m.find()) vals.add(Integer.parseInt(m.group()));
                if (vals.size() < 5) continue;
                List<Integer> lastFive = vals.subList(vals.size()-5, vals.size());
                Set<Integer> unique = new HashSet<>(lastFive);
                if (unique.size() != 5) continue;
                boolean ok = true;
                int[] draw = new int[5];
                for (int i=0;i<5;i++) {
                    int n = lastFive.get(i);
                    if (n < 1 || n > 80) { ok=false; break; }
                    draw[i]=n;
                }
                if (!ok) continue;
                Arrays.sort(draw);
                out.add(draw);
                if (lineNo % 1000 == 0) {
                    int valid = out.size();
                    main.post(() -> setStatus(Math.min(90, 5 + valid/50), "Lendo histórico... " + valid + " concursos válidos"));
                }
            }
        }
        return out;
    }

    private void analyze() {
        if (running || history.isEmpty()) return;
        running = true;
        lastResult = null;
        setControls(false);
        pdfButton.setEnabled(false);
        resultText.setText("");

        executor.execute(() -> {
            try {
                AnalysisEngine.Result result = AnalysisEngine.analyze(history, (percent, message) ->
                        main.post(() -> setStatus(percent, message)));
                lastResult = result;
                main.post(() -> {
                    resultText.setText(result.summary);
                    running = false;
                    importButton.setEnabled(true);
                    analyzeButton.setEnabled(true);
                    pdfButton.setEnabled(true);
                    setStatus(100, "Análise concluída. Jogo e PDF prontos.");
                });
            } catch (Throwable t) {
                main.post(() -> {
                    running = false;
                    setControls(true);
                    analyzeButton.setEnabled(true);
                    pdfButton.setEnabled(false);
                    setStatus(0, "Erro na análise: " + safeMessage(t));
                    Toast.makeText(this, "A análise foi interrompida com segurança.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void generatePdf() {
        if (running || lastResult == null) return;
        running = true;
        setControls(false);
        setStatus(10, "Gerando PDF verde/vermelho...");
        executor.execute(() -> {
            try {
                String location = PdfReport.create(this, lastResult);
                main.post(() -> {
                    running = false;
                    setControls(true);
                    analyzeButton.setEnabled(true);
                    pdfButton.setEnabled(true);
                    setStatus(100, "PDF salvo em: " + location);
                    Toast.makeText(this, "PDF gerado com sucesso.", Toast.LENGTH_LONG).show();
                });
            } catch (Throwable t) {
                main.post(() -> {
                    running = false;
                    setControls(true);
                    analyzeButton.setEnabled(true);
                    pdfButton.setEnabled(true);
                    setStatus(0, "Erro ao gerar PDF: " + safeMessage(t));
                });
            }
        });
    }

    private void setControls(boolean enabled) {
        importButton.setEnabled(enabled);
        analyzeButton.setEnabled(enabled && !history.isEmpty());
        pdfButton.setEnabled(enabled && lastResult != null);
    }

    private void setStatus(int progress, String message) {
        progressBar.setProgress(Math.max(0, Math.min(100, progress)));
        statusText.setText(message);
    }

    private void showFatal(Throwable t) {
        Toast.makeText(this, "Erro ao abrir o app: " + safeMessage(t), Toast.LENGTH_LONG).show();
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private String safeMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.trim().isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private String formatGame(int[] game) {
        StringBuilder sb = new StringBuilder();
        for (int n : game) {
            if (sb.length()>0) sb.append(' ');
            sb.append(String.format(java.util.Locale.US, "%02d", n));
        }
        return sb.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
