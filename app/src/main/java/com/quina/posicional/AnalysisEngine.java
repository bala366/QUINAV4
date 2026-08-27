package com.quina.posicional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AnalysisEngine {

    public interface ProgressListener {
        void onProgress(int percent, String message);
    }

    public static final class Result {
        public final int[] game;
        public final double finalScore;
        public final double positionalScore;
        public final double taloScore;
        public final double patternScore;
        public final double cycleScore;
        public final String summary;

        Result(int[] game, double finalScore, double positionalScore,
               double taloScore, double patternScore, double cycleScore,
               String summary) {
            this.game = game;
            this.finalScore = finalScore;
            this.positionalScore = positionalScore;
            this.taloScore = taloScore;
            this.patternScore = patternScore;
            this.cycleScore = cycleScore;
            this.summary = summary;
        }
    }

    private static final int MAX_CANDIDATES = 50000;
    private static final int TOP_PER_POSITION = 14;
    private static final int RECENT_WINDOW = 40;
    private static final int TALO_WINDOW = 18;

    private AnalysisEngine() {}

    public static Result analyze(List<int[]> history, ProgressListener progress) {
        if (history == null || history.size() < 30) {
            throw new IllegalArgumentException("Use pelo menos 30 concursos validos.");
        }

        progress.onProgress(5, "Classificando P01 ate P05 na historia inteira...");
        PositionModel[] models = buildPositionModels(history);

        progress.onProgress(18, "Medindo Pareto historico e comportamento recente...");
        for (PositionModel m : models) {
            m.finish(history);
        }

        progress.onProgress(30, "Estudando os dois ultimos ciclos completos...");
        List<Cycle> cycles = detectCompletedCycles(history);
        List<Cycle> lastTwo = new ArrayList<>();
        for (int i = Math.max(0, cycles.size() - 2); i < cycles.size(); i++) {
            lastTwo.add(cycles.get(i));
        }

        progress.onProgress(38, "Reduzindo universo pelo Pareto posicional...");
        List<int[]> pools = new ArrayList<>();
        for (int p = 0; p < 5; p++) {
            pools.add(models[p].topNumbers(TOP_PER_POSITION));
        }

        List<int[]> candidates = new ArrayList<>(MAX_CANDIDATES);
        generateCandidates(pools, 0, new int[5], -1, candidates);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Nenhum candidato posicional foi formado.");
        }

        progress.onProgress(45, "Candidatos reduzidos: " + candidates.size() + ". Iniciando talo...");

        PatternStats stats = PatternStats.fromHistory(history);
        double best = -1;
        int[] bestGame = null;
        double bestPos = 0, bestTalo = 0, bestPattern = 0, bestCycle = 0;

        int total = candidates.size();
        for (int i = 0; i < total; i++) {
            int[] game = candidates.get(i);
            double pos = positionalScore(game, models);
            double talo = taloScore(game, history);
            double pattern = patternScore(game, stats);
            double cycle = cycleScore(game, history, lastTwo);
            double score = 0.42 * pos + 0.28 * talo + 0.20 * pattern + 0.10 * cycle;

            if (score > best) {
                best = score;
                bestGame = game.clone();
                bestPos = pos;
                bestTalo = talo;
                bestPattern = pattern;
                bestCycle = cycle;
            }

            if (i % Math.max(1, total / 20) == 0 || i == total - 1) {
                int pct = 45 + (int) Math.round(45.0 * (i + 1) / total);
                progress.onProgress(Math.min(90, pct),
                        "Engrossando o talo: " + (i + 1) + "/" + total +
                                " candidatos (" + Math.round(100.0 * (i + 1) / total) + "%)");
            }
        }

        progress.onProgress(94, "Cruzando posicional, talo, ciclos e padroes...");

        String summary = buildSummary(bestGame, best, bestPos, bestTalo, bestPattern, bestCycle,
                models, stats, lastTwo, history);

        progress.onProgress(100, "Analise concluida. PDF liberado.");
        return new Result(bestGame, best, bestPos, bestTalo, bestPattern, bestCycle, summary);
    }

    private static PositionModel[] buildPositionModels(List<int[]> history) {
        PositionModel[] models = new PositionModel[5];
        for (int p = 0; p < 5; p++) models[p] = new PositionModel(p);
        for (int[] draw : history) {
            int[] sorted = draw.clone();
            Arrays.sort(sorted);
            for (int p = 0; p < 5; p++) models[p].add(sorted[p]);
        }
        return models;
    }

    private static void generateCandidates(List<int[]> pools, int pos, int[] current,
                                           int previous, List<int[]> out) {
        if (out.size() >= MAX_CANDIDATES) return;
        if (pos == 5) {
            out.add(current.clone());
            return;
        }
        for (int n : pools.get(pos)) {
            if (n <= previous) continue;
            current[pos] = n;
            generateCandidates(pools, pos + 1, current, n, out);
            if (out.size() >= MAX_CANDIDATES) return;
        }
    }

    private static double positionalScore(int[] game, PositionModel[] models) {
        double sum = 0;
        for (int p = 0; p < 5; p++) sum += models[p].score(game[p]);
        return 100.0 * sum / 5.0;
    }

    private static double taloScore(int[] game, List<int[]> history) {
        int start = Math.max(0, history.size() - TALO_WINDOW);
        List<Integer> hits = new ArrayList<>();
        Set<Integer> g = new HashSet<>();
        for (int n : game) g.add(n);
        for (int i = start; i < history.size(); i++) {
            int h = 0;
            for (int n : history.get(i)) if (g.contains(n)) h++;
            hits.add(h);
        }
        if (hits.isEmpty()) return 0;

        double weighted = 0, weightSum = 0;
        for (int i = 0; i < hits.size(); i++) {
            double w = i + 1;
            weighted += hits.get(i) * w;
            weightSum += w;
        }
        double recent = weighted / (5.0 * weightSum);
        double slope = linearSlope(hits);
        double persistence = 0;
        for (int h : hits) if (h >= 1) persistence += 1;
        persistence /= hits.size();

        double score = 68 * recent + 22 * persistence + 10 * clamp01(0.5 + slope / 2.0);
        return clamp100(score);
    }

    private static double linearSlope(List<Integer> y) {
        int n = y.size();
        if (n < 2) return 0;
        double xm = (n - 1) / 2.0;
        double ym = 0;
        for (int v : y) ym += v;
        ym /= n;
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - xm) * (y.get(i) - ym);
            den += (i - xm) * (i - xm);
        }
        return den == 0 ? 0 : num / den;
    }

    private static double patternScore(int[] game, PatternStats stats) {
        int sum = 0, odds = 0, primes = 0;
        for (int n : game) {
            sum += n;
            if ((n & 1) == 1) odds++;
            if (isPrime(n)) primes++;
        }
        int span = game[4] - game[0];
        double s1 = gaussian(sum, stats.sumMean, stats.sumSd);
        double s2 = gaussian(odds, stats.oddsMean, stats.oddsSd);
        double s3 = gaussian(primes, stats.primeMean, stats.primeSd);
        double s4 = gaussian(span, stats.spanMean, stats.spanSd);
        return 100.0 * (s1 + s2 + s3 + s4) / 4.0;
    }

    private static double cycleScore(int[] game, List<int[]> history, List<Cycle> cycles) {
        if (cycles.isEmpty()) return 50;
        double total = 0, weight = 0;
        for (int c = 0; c < cycles.size(); c++) {
            Cycle cycle = cycles.get(c);
            Map<Integer,Integer> freq = new HashMap<>();
            for (int i = cycle.start; i <= cycle.end; i++) {
                for (int n : history.get(i)) freq.put(n, freq.getOrDefault(n, 0) + 1);
            }
            int max = 1;
            for (int v : freq.values()) max = Math.max(max, v);
            double gameScore = 0;
            for (int n : game) gameScore += freq.getOrDefault(n, 0) / (double) max;
            gameScore /= 5.0;
            double w = c + 1;
            total += w * gameScore;
            weight += w;
        }
        return 100.0 * total / weight;
    }

    private static List<Cycle> detectCompletedCycles(List<int[]> history) {
        List<Cycle> cycles = new ArrayList<>();
        Set<Integer> missing = new HashSet<>();
        for (int n = 1; n <= 80; n++) missing.add(n);
        int start = 0;
        for (int i = 0; i < history.size(); i++) {
            for (int n : history.get(i)) missing.remove(n);
            if (missing.isEmpty()) {
                cycles.add(new Cycle(start, i));
                start = i + 1;
                missing.clear();
                for (int n = 1; n <= 80; n++) missing.add(n);
            }
        }
        return cycles;
    }

    private static String buildSummary(int[] game, double finalScore, double pos, double talo,
                                       double pattern, double cycle, PositionModel[] models,
                                       PatternStats stats, List<Cycle> cycles, List<int[]> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUINA POSICIONAL V4\n\n");
        sb.append("JOGO SUGERIDO: ");
        for (int n : game) sb.append(String.format(Locale.US, "%02d ", n));
        sb.append("\n\n");
        sb.append(String.format(Locale.US, "Score final: %.2f\n", finalScore));
        sb.append(String.format(Locale.US, "Posicional P01-P05: %.2f\n", pos));
        sb.append(String.format(Locale.US, "Engrossamento/arrasto: %.2f\n", talo));
        sb.append(String.format(Locale.US, "Padroes da Quina: %.2f\n", pattern));
        sb.append(String.format(Locale.US, "Ultimos ciclos: %.2f\n\n", cycle));
        sb.append("CENTROS POSICIONAIS\n");
        for (int p = 0; p < 5; p++) {
            sb.append(String.format(Locale.US, "P%02d centro %.2f | jogo %02d\n",
                    p + 1, models[p].mean, game[p]));
        }
        sb.append("\nHistorico analisado: ").append(history.size()).append(" concursos\n");
        sb.append("Ciclos completos usados: ").append(cycles.size()).append("\n");
        sb.append("Observacao: estudo estatistico de tendencia; nao garante sorteio.\n");
        return sb.toString();
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int i = 2; i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }

    private static double gaussian(double x, double mean, double sd) {
        sd = Math.max(sd, 0.75);
        double z = (x - mean) / sd;
        return Math.exp(-0.5 * z * z);
    }

    private static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
    private static double clamp100(double x) { return Math.max(0, Math.min(100, x)); }

    private static final class Cycle {
        final int start, end;
        Cycle(int start, int end) { this.start = start; this.end = end; }
    }

    private static final class PositionModel {
        final int position;
        final int[] freq = new int[81];
        final int[] recent = new int[81];
        int total;
        double mean;
        int maxFreq = 1;
        int maxRecent = 1;

        PositionModel(int position) { this.position = position; }

        void add(int n) { freq[n]++; total++; }

        void finish(List<int[]> history) {
            double sum = 0;
            for (int n = 1; n <= 80; n++) {
                maxFreq = Math.max(maxFreq, freq[n]);
                sum += n * freq[n];
            }
            mean = total == 0 ? 0 : sum / total;
            int start = Math.max(0, history.size() - RECENT_WINDOW);
            for (int i = start; i < history.size(); i++) {
                int[] d = history.get(i).clone();
                Arrays.sort(d);
                recent[d[position]]++;
            }
            for (int n = 1; n <= 80; n++) maxRecent = Math.max(maxRecent, recent[n]);
        }

        double score(int n) {
            double hist = freq[n] / (double) maxFreq;
            double rec = recent[n] / (double) maxRecent;
            double center = Math.exp(-0.5 * Math.pow((n - mean) / 8.0, 2));
            return clamp01(0.48 * hist + 0.32 * rec + 0.20 * center);
        }

        int[] topNumbers(int k) {
            List<Integer> nums = new ArrayList<>();
            for (int n = 1; n <= 80; n++) nums.add(n);
            nums.sort((a,b) -> Double.compare(score(b), score(a)));
            int size = Math.min(k, nums.size());
            int[] out = new int[size];
            for (int i = 0; i < size; i++) out[i] = nums.get(i);
            Arrays.sort(out);
            return out;
        }
    }

    private static final class PatternStats {
        double sumMean, sumSd, oddsMean, oddsSd, primeMean, primeSd, spanMean, spanSd;

        static PatternStats fromHistory(List<int[]> history) {
            List<Double> sums = new ArrayList<>(), odds = new ArrayList<>(), primes = new ArrayList<>(), spans = new ArrayList<>();
            for (int[] raw : history) {
                int[] d = raw.clone(); Arrays.sort(d);
                int s = 0, o = 0, p = 0;
                for (int n : d) { s += n; if ((n & 1) == 1) o++; if (isPrime(n)) p++; }
                sums.add((double)s); odds.add((double)o); primes.add((double)p); spans.add((double)(d[4]-d[0]));
            }
            PatternStats ps = new PatternStats();
            ps.sumMean = mean(sums); ps.sumSd = sd(sums, ps.sumMean);
            ps.oddsMean = mean(odds); ps.oddsSd = sd(odds, ps.oddsMean);
            ps.primeMean = mean(primes); ps.primeSd = sd(primes, ps.primeMean);
            ps.spanMean = mean(spans); ps.spanSd = sd(spans, ps.spanMean);
            return ps;
        }

        static double mean(List<Double> xs) { double s=0; for(double x:xs)s+=x; return s/xs.size(); }
        static double sd(List<Double> xs,double m){ double s=0; for(double x:xs)s+=(x-m)*(x-m); return Math.sqrt(s/Math.max(1,xs.size()-1)); }
    }
}
