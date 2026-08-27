package com.quina.posicional;

import java.util.*;
import java.util.regex.*;

public class QuinaEngine {
    public interface Progress { void onProgress(int percent, String message); }

    public static class Concurso {
        public int numero;
        public int[] dezenas;
        Concurso(int numero, int[] dezenas) { this.numero = numero; this.dezenas = dezenas; }
    }

    public static class Resultado {
        public int[] jogo;
        public double scoreFinal, scorePosicional, scoreTalo, scoreCiclos, scorePadrao;
        public double[] centros = new double[5];
        public int[] arrasto;
        public String resumo;
    }

    private static final Set<Integer> PRIMOS = new HashSet<>(Arrays.asList(2,3,5,7,11,13,17,19,23,29,31,37,41,43,47,53,59,61,67,71,73,79));
    private static final Set<Integer> FIB = new HashSet<>(Arrays.asList(1,2,3,5,8,13,21,34,55));

    public static List<Concurso> parseHistorico(String text) {
        List<Concurso> out = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        Pattern p = Pattern.compile("\\d+");
        int auto = 1;
        for (String line : lines) {
            Matcher m = p.matcher(line);
            ArrayList<Integer> vals = new ArrayList<>();
            while (m.find()) {
                try { vals.add(Integer.parseInt(m.group())); } catch(Exception ignored) {}
            }
            if (vals.size() < 5) continue;
            int concurso;
            int start;
            if (vals.size() >= 6) { concurso = vals.get(0); start = vals.size() - 5; }
            else { concurso = auto++; start = 0; }
            TreeSet<Integer> set = new TreeSet<>();
            for (int i=start; i<vals.size(); i++) {
                int n = vals.get(i);
                if (n >= 1 && n <= 80) set.add(n);
            }
            if (set.size() == 5) {
                int[] d = new int[5]; int k=0;
                for (int n: set) d[k++] = n;
                out.add(new Concurso(concurso,d));
            }
        }
        Collections.sort(out, (a,b) -> Integer.compare(a.numero,b.numero));
        ArrayList<Concurso> clean = new ArrayList<>();
        HashSet<Integer> vistos = new HashSet<>();
        for (Concurso c: out) if (!vistos.contains(c.numero)) { vistos.add(c.numero); clean.add(c); }
        return clean;
    }

    public static Resultado analisar(List<Concurso> hist, Progress progress) {
        if (hist == null || hist.size() < 30) throw new RuntimeException("Historico pequeno. Use pelo menos 30 concursos.");
        progress.onProgress(5, "Histórico carregado: " + hist.size() + " concursos. Estudando P01-P05...");

        int total = hist.size();
        double[][] posFreq = new double[5][81];
        double[][] posRecent = new double[5][81];
        double[] centros = new double[5];
        int recentStart = Math.max(0,total-120);

        for (int i=0;i<total;i++) {
            Concurso c = hist.get(i);
            double pesoHist = 1.0;
            double pesoRec = i >= recentStart ? 1.0 + (i-recentStart)/(double)Math.max(1,total-recentStart) : 0.0;
            for (int p=0;p<5;p++) {
                int n = c.dezenas[p];
                posFreq[p][n] += pesoHist;
                posRecent[p][n] += pesoRec;
                if (i >= total-40) centros[p] += n;
            }
        }
        for (int p=0;p<5;p++) centros[p] /= Math.min(40,total);
        progress.onProgress(15, "Pareto posicional pronto. Estudando ciclos...");

        HashSet<Integer> cicloAtualFaltam = new HashSet<>();
        for (int n=1;n<=80;n++) cicloAtualFaltam.add(n);
        ArrayList<int[]> ciclos = new ArrayList<>();
        int inicio = 0;
        for (int i=0;i<total;i++) {
            for (int n: hist.get(i).dezenas) cicloAtualFaltam.remove(n);
            if (cicloAtualFaltam.isEmpty()) {
                ciclos.add(new int[]{inicio,i});
                inicio = i+1;
                cicloAtualFaltam.clear(); for (int n=1;n<=80;n++) cicloAtualFaltam.add(n);
            }
        }
        ArrayList<HashMap<Integer,Integer>> cicloFreqs = new ArrayList<>();
        for (int ci=Math.max(0,ciclos.size()-2); ci<ciclos.size(); ci++) {
            int[] inter = ciclos.get(ci);
            HashMap<Integer,Integer> f = new HashMap<>();
            for (int i=inter[0];i<=inter[1];i++) for (int n: hist.get(i).dezenas) f.put(n, f.getOrDefault(n,0)+1);
            cicloFreqs.add(f);
        }
        progress.onProgress(25, "Reduzindo universo pelo posicional antes do talo...");

        ArrayList<Integer>[] pools = new ArrayList[5];
        for (int p=0;p<5;p++) {
            ArrayList<Integer> nums = new ArrayList<>();
            for (int n=1;n<=80;n++) nums.add(n);
            final int pp = p;
            Collections.sort(nums, (a,b) -> Double.compare(scoreNumeroPos(b,pp,posFreq,posRecent,centros,cicloFreqs), scoreNumeroPos(a,pp,posFreq,posRecent,centros,cicloFreqs)));
            pools[p] = new ArrayList<>(nums.subList(0, Math.min(18, nums.size())));
            Collections.sort(pools[p]);
        }

        progress.onProgress(35, "Gerando candidatos fortes P01-P05...");
        HashMap<String,int[]> mapa = new HashMap<>();
        int gerados = 0;
        for (int a: pools[0]) for (int b: pools[1]) if (a<b) for (int c: pools[2]) if (b<c) for (int d: pools[3]) if (c<d) for (int e: pools[4]) if (d<e) {
            int[] j = new int[]{a,b,c,d,e};
            if (padraoBasico(j)) mapa.put(key(j),j);
            gerados++;
            if (gerados % 5000 == 0) progress.onProgress(35 + Math.min(10, gerados/5000), "Candidatos posicionais gerados: " + gerados + " | aprovados: " + mapa.size());
        }
        ArrayList<int[]> candidatos = new ArrayList<>(mapa.values());
        if (candidatos.isEmpty()) throw new RuntimeException("Nenhum candidato após filtros. Histórico pode estar em formato errado.");
        progress.onProgress(48, "Candidatos reduzidos: " + candidatos.size() + ". Medindo engrossamento do talo...");

        ArrayList<Scored> scored = new ArrayList<>();
        int count = 0;
        int step = Math.max(1, candidatos.size()/20);
        for (int[] j: candidatos) {
            double pos = scorePosicional(j,posFreq,posRecent,centros);
            Talo talo = scoreTalo(j,hist);
            double ciclo = scoreCiclo(j,cicloFreqs,cicloAtualFaltam);
            double padrao = scorePadrao(j,hist);
            double finalScore = 0.38*pos + 0.32*talo.score + 0.16*ciclo + 0.14*padrao;
            scored.add(new Scored(j,finalScore,pos,talo.score,ciclo,padrao,talo.hits));
            count++;
            if (count % step == 0 || count == candidatos.size()) {
                int pct = 50 + (int)(35.0*count/candidatos.size());
                progress.onProgress(pct, "Talo/arrasto: " + count + "/" + candidatos.size() + " candidatos.");
            }
        }
        Collections.sort(scored, (x,y) -> Double.compare(y.score,x.score));
        Scored best = scored.get(0);
        progress.onProgress(90, "Ranking final pronto. Montando resumo e liberando PDF...");

        Resultado r = new Resultado();
        r.jogo = best.jogo;
        r.scoreFinal = best.score;
        r.scorePosicional = best.pos;
        r.scoreTalo = best.talo;
        r.scoreCiclos = best.ciclo;
        r.scorePadrao = best.padrao;
        r.centros = centros;
        r.arrasto = best.hits;
        r.resumo = montarResumo(r, hist.get(total-1));
        progress.onProgress(100, "Análise concluída.");
        return r;
    }

    private static double scoreNumeroPos(int n, int p, double[][] posFreq, double[][] posRecent, double[] centros, ArrayList<HashMap<Integer,Integer>> ciclos) {
        double s = 0.55*normal(posFreq[p][n], posFreq[p]) + 0.25*normal(posRecent[p][n], posRecent[p]);
        double dist = Math.abs(n-centros[p]);
        s += 0.15*Math.exp(-dist*dist/120.0);
        int cf=0; for (HashMap<Integer,Integer> m: ciclos) cf += m.getOrDefault(n,0);
        s += 0.05*cf;
        return s;
    }
    private static double normal(double v, double[] arr) { double max=0; for (double x: arr) if (x>max) max=x; return max==0?0:v/max; }
    private static String key(int[] j){ return j[0]+"-"+j[1]+"-"+j[2]+"-"+j[3]+"-"+j[4]; }

    private static boolean padraoBasico(int[] j) {
        int soma=0, pares=0, seq=1, maxSeq=1;
        for (int i=0;i<5;i++){ soma+=j[i]; if(j[i]%2==0) pares++; if(i>0){ if(j[i]==j[i-1]+1) seq++; else seq=1; if(seq>maxSeq) maxSeq=seq; } }
        return soma>=60 && soma<=320 && pares>=1 && pares<=4 && maxSeq<=3;
    }

    private static double scorePosicional(int[] j, double[][] posFreq, double[][] posRecent, double[] centros) {
        double s=0;
        for (int p=0;p<5;p++) {
            int n=j[p];
            double dist = Math.abs(n-centros[p]);
            s += 55*normal(posFreq[p][n],posFreq[p]) + 25*normal(posRecent[p][n],posRecent[p]) + 20*Math.exp(-dist*dist/100.0);
        }
        return s/5;
    }

    private static class Talo { double score; int[] hits; Talo(double s,int[] h){score=s;hits=h;} }
    private static Talo scoreTalo(int[] j, List<Concurso> hist) {
        int janela = Math.min(40,hist.size()-1);
        int start = hist.size()-1-janela;
        int[] hits = new int[janela];
        HashSet<Integer> set = new HashSet<>(); for (int n:j) set.add(n);
        for (int i=0;i<janela;i++) {
            int h=0; for(int n: hist.get(start+i).dezenas) if(set.contains(n)) h++; hits[i]=h;
        }
        double rec=0, pesos=0; for(int i=0;i<hits.length;i++){ double w=i+1; rec += hits[i]*w; pesos+=w; }
        rec = 100*rec/(5*pesos);
        double slope = inclinacaoArray(hits);
        int forte=0; for(int h:hits) if(h>=2) forte++;
        double persist = 100.0*forte/hits.length;
        double score = 0.55*rec + 0.25*persist + 20*Math.max(0, slope);
        return new Talo(score,hits);
    }
    private static double inclinacaoArray(int[] vals){ double[] d=new double[vals.length]; for(int i=0;i<vals.length;i++) d[i]=vals[i]; return inclinacaoDouble(d); }
    private static double inclinacaoDouble(double[] vals){ int n=vals.length; if(n<2)return 0; double xm=(n-1)/2.0, ym=0; for(double v:vals)ym+=v; ym/=n; double den=0,num=0; for(int i=0;i<n;i++){ den+=(i-xm)*(i-xm); num+=(i-xm)*(vals[i]-ym);} return den==0?0:num/den; }

    private static double scoreCiclo(int[] j, ArrayList<HashMap<Integer,Integer>> ciclos, HashSet<Integer> faltando) {
        double s=50;
        if (!ciclos.isEmpty()) {
            s=0; double[] pesos={0.65,0.35}; int idx=0;
            for(int c=ciclos.size()-1;c>=0;c--) {
                HashMap<Integer,Integer> m=ciclos.get(c); int pts=0; for(int n:j) pts += m.getOrDefault(n,0);
                s += pesos[Math.min(idx,pesos.length-1)] * Math.min(100, pts*12.0); idx++;
            }
        }
        int f=0; for(int n:j) if(faltando.contains(n)) f++;
        return 0.85*s + 0.15*(100.0*f/5.0);
    }

    private static double scorePadrao(int[] j, List<Concurso> hist) {
        int soma=0, pares=0, primos=0, fib=0, baixos=0;
        for(int n:j){ soma+=n; if(n%2==0)pares++; if(PRIMOS.contains(n))primos++; if(FIB.contains(n))fib++; if(n<=40)baixos++; }
        double alvoSoma=0, alvoPares=0, alvoPrimos=0, alvoFib=0, alvoBaixos=0;
        int janela=Math.min(80,hist.size());
        for(int i=hist.size()-janela;i<hist.size();i++){
            int[] d=hist.get(i).dezenas; int s=0,p=0,pr=0,fi=0,ba=0;
            for(int n:d){ s+=n; if(n%2==0)p++; if(PRIMOS.contains(n))pr++; if(FIB.contains(n))fi++; if(n<=40)ba++; }
            alvoSoma+=s; alvoPares+=p; alvoPrimos+=pr; alvoFib+=fi; alvoBaixos+=ba;
        }
        alvoSoma/=janela; alvoPares/=janela; alvoPrimos/=janela; alvoFib/=janela; alvoBaixos/=janela;
        double sc=0;
        sc += Math.exp(-Math.pow((soma-alvoSoma)/35.0,2))*35;
        sc += Math.exp(-Math.pow((pares-alvoPares)/1.4,2))*20;
        sc += Math.exp(-Math.pow((primos-alvoPrimos)/1.4,2))*15;
        sc += Math.exp(-Math.pow((fib-alvoFib)/1.2,2))*15;
        sc += Math.exp(-Math.pow((baixos-alvoBaixos)/1.4,2))*15;
        return sc;
    }

    private static String montarResumo(Resultado r, Concurso ultimo) {
        StringBuilder sb = new StringBuilder();
        sb.append("Jogo sugerido: ").append(format(r.jogo)).append("\n");
        sb.append("Último concurso: ").append(ultimo.numero).append(" | ").append(format(ultimo.dezenas)).append("\n");
        sb.append("Score final: ").append(String.format(Locale.US,"%.2f",r.scoreFinal)).append("\n");
        sb.append("Posicional P01-P05: ").append(String.format(Locale.US,"%.2f",r.scorePosicional)).append("\n");
        sb.append("Engrossamento do talo: ").append(String.format(Locale.US,"%.2f",r.scoreTalo)).append("\n");
        sb.append("Ciclos: ").append(String.format(Locale.US,"%.2f",r.scoreCiclos)).append("\n");
        sb.append("Padrões da Quina: ").append(String.format(Locale.US,"%.2f",r.scorePadrao)).append("\n");
        sb.append("Arrasto: ").append(Arrays.toString(r.arrasto)).append("\n");
        for(int i=0;i<5;i++) sb.append("P").append(String.format(Locale.US,"%02d",i+1)).append(" centro ").append(String.format(Locale.US,"%.2f",r.centros[i])).append("\n");
        return sb.toString();
    }
    public static String format(int[] arr){ StringBuilder sb=new StringBuilder(); for(int n:arr){ if(sb.length()>0)sb.append(' '); sb.append(String.format(Locale.US,"%02d",n)); } return sb.toString(); }

    private static class Scored { int[] jogo,hits; double score,pos,talo,ciclo,padrao; Scored(int[] j,double s,double p,double t,double c,double pa,int[] h){jogo=j;score=s;pos=p;talo=t;ciclo=c;padrao=pa;hits=h;} }
}
