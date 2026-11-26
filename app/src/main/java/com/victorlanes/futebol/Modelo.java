package com.victorlanes.futebol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Jogadores, clubes e o mundo do jogo (tudo salvo em JSON). */
public final class Modelo {
    private Modelo() {
    }

    public static final String[] POS = {"GOL", "ZAG", "LAT", "VOL", "MEI", "ATA"};
    public static final String[] POS_NOME = {"Goleiro", "Zagueiro", "Lateral", "Volante", "Meia", "Atacante"};
    public static final int[] POS_COR = {0xFFF59E0B, 0xFF3B82F6, 0xFF06B6D4, 0xFF10B981, 0xFF8B5CF6, 0xFFEF4444};
    /** pesos de {velocidade, finalização, passe, drible, defesa, goleiro} por posição */
    static final float[][] PESOS = {
            {.05f, 0, .1f, 0, .05f, .8f},
            {.2f, 0, .15f, .05f, .6f, 0},
            {.3f, 0, .2f, .15f, .35f, 0},
            {.15f, .05f, .3f, .15f, .35f, 0},
            {.15f, .2f, .35f, .25f, .05f, 0},
            {.25f, .45f, .1f, .2f, 0, 0}};
    public static final String[] ATR = {"Velocidade", "Finalização", "Passe", "Drible", "Defesa", "Goleiro"};

    // ================================================================== JOGADOR

    public static final class Jogador {
        public int id, idade, pos, pot, numero;
        public int[] a = new int[6]; // vel, fin, pas, dri, def, gk
        public String nome = "";
        public int gols, jogos, assist; // na temporada
        public int golsCarreira, jogosCarreira;
        public float evolucao; // progresso acumulado do treino (0..1 = +1 ponto)

        public int ovr() {
            float s = 0;
            for (int i = 0; i < 6; i++) s += a[i] * PESOS[pos][i];
            return Math.round(s);
        }

        /** nota jogando fora de posição */
        public int ovrEm(int p) {
            float s = 0;
            for (int i = 0; i < 6; i++) s += a[i] * PESOS[p][i];
            int r = Math.round(s);
            if (p != pos) r -= (p == 0 || pos == 0) ? 25 : vizinha(p, pos) ? 3 : 8;
            return Math.max(10, r);
        }

        static boolean vizinha(int a, int b) {
            return (a == 1 && b == 2) || (a == 2 && b == 1) || (a == 3 && b == 4) || (a == 4 && b == 3) || (a == 4 && b == 5) || (a == 5 && b == 4) || (a == 3 && b == 1) || (a == 1 && b == 3);
        }

        public long valor() {
            double v = 20000 * Math.pow(1.12, ovr() - 30);
            if (idade <= 21) v *= 1.5;
            else if (idade <= 24) v *= 1.25;
            else if (idade >= 33) v *= 0.45;
            else if (idade >= 30) v *= 0.7;
            v *= 1 + Math.max(0, pot - ovr()) / 40.0;
            return Math.max(10000, Math.round(v / 1000) * 1000);
        }

        public long salario() {
            return Math.max(1000, Math.round(valor() / 260.0 / 100) * 100);
        }

        JSONObject json() throws JSONException {
            JSONArray at = new JSONArray();
            for (int x : a) at.put(x);
            return new JSONObject().put("id", id).put("n", nome).put("i", idade).put("p", pos).put("pt", pot).put("nu", numero).put("a", at)
                    .put("g", gols).put("j", jogos).put("as", assist).put("gc", golsCarreira).put("jc", jogosCarreira).put("ev", evolucao);
        }

        static Jogador de(JSONObject o) {
            Jogador j = new Jogador();
            j.id = o.optInt("id");
            j.nome = o.optString("n");
            j.idade = o.optInt("i");
            j.pos = o.optInt("p");
            j.pot = o.optInt("pt");
            j.numero = o.optInt("nu");
            JSONArray at = o.optJSONArray("a");
            for (int i = 0; i < 6 && at != null; i++) j.a[i] = at.optInt(i);
            j.gols = o.optInt("g");
            j.jogos = o.optInt("j");
            j.assist = o.optInt("as");
            j.golsCarreira = o.optInt("gc");
            j.jogosCarreira = o.optInt("jc");
            j.evolucao = (float) o.optDouble("ev", 0);
            return j;
        }
    }

    // ================================================================== CLUBE

    public static final String[] FORMACOES = {"4-4-2", "4-3-3", "4-2-3-1", "3-5-2", "5-3-2", "4-1-2-1-2"};

    /** posições (x = profundidade 0..1 a partir do próprio gol, y = 0..1 largura) e a posição ideal de cada vaga */
    public static float[][] vagas(String f) {
        switch (f) {
            case "4-3-3":
                return new float[][]{{.04f, .5f, 0}, {.22f, .38f, 1}, {.22f, .62f, 1}, {.25f, .12f, 2}, {.25f, .88f, 2},
                        {.42f, .5f, 3}, {.5f, .3f, 4}, {.5f, .7f, 4}, {.72f, .15f, 5}, {.78f, .5f, 5}, {.72f, .85f, 5}};
            case "4-2-3-1":
                return new float[][]{{.04f, .5f, 0}, {.22f, .38f, 1}, {.22f, .62f, 1}, {.25f, .12f, 2}, {.25f, .88f, 2},
                        {.4f, .38f, 3}, {.4f, .62f, 3}, {.6f, .18f, 4}, {.6f, .5f, 4}, {.6f, .82f, 4}, {.8f, .5f, 5}};
            case "3-5-2":
                return new float[][]{{.04f, .5f, 0}, {.22f, .28f, 1}, {.2f, .5f, 1}, {.22f, .72f, 1},
                        {.45f, .1f, 2}, {.45f, .9f, 2}, {.4f, .5f, 3}, {.55f, .32f, 4}, {.55f, .68f, 4}, {.78f, .38f, 5}, {.78f, .62f, 5}};
            case "5-3-2":
                return new float[][]{{.04f, .5f, 0}, {.2f, .3f, 1}, {.19f, .5f, 1}, {.2f, .7f, 1}, {.28f, .08f, 2}, {.28f, .92f, 2},
                        {.45f, .5f, 3}, {.52f, .28f, 4}, {.52f, .72f, 4}, {.76f, .38f, 5}, {.76f, .62f, 5}};
            case "4-1-2-1-2":
                return new float[][]{{.04f, .5f, 0}, {.22f, .38f, 1}, {.22f, .62f, 1}, {.27f, .12f, 2}, {.27f, .88f, 2},
                        {.38f, .5f, 3}, {.5f, .28f, 4}, {.5f, .72f, 4}, {.62f, .5f, 4}, {.78f, .38f, 5}, {.78f, .62f, 5}};
            default: // 4-4-2
                return new float[][]{{.04f, .5f, 0}, {.22f, .38f, 1}, {.22f, .62f, 1}, {.25f, .12f, 2}, {.25f, .88f, 2},
                        {.45f, .38f, 3}, {.48f, .62f, 4}, {.55f, .12f, 4}, {.55f, .88f, 4}, {.78f, .38f, 5}, {.78f, .62f, 5}};
        }
    }

    public static final class Clube {
        public int id, divisao, reputacao;
        public String nome = "", sigla = "", uf = "", pais = "Brasil";
        public int cor1, cor2;
        public List<Jogador> elenco = new ArrayList<>();
        public int[] titular = new int[11]; // ids dos jogadores por vaga
        public String formacao = "4-4-2";
        public float[][] posicoes; // posições livres da tática (null = formação padrão)
        public long dinheiro;
        public int estadio = 1, ct = 1, base = 1; // níveis 1..10
        public int precoIngresso = 30;
        public boolean usuario;

        public Jogador jogador(int id) {
            for (Jogador j : elenco) if (j.id == id) return j;
            return null;
        }

        /** vagas efetivas: posições livres (se o técnico mexeu) ou da formação, com a função de cada uma */
        public float[][] vagas() {
            float[][] base = Modelo.vagas(formacao);
            if (posicoes == null || posicoes.length != 11) return base;
            float[][] v = new float[11][3];
            for (int i = 0; i < 11; i++) {
                v[i][0] = posicoes[i][0];
                v[i][1] = posicoes[i][1];
                v[i][2] = i == 0 ? 0 : funcao(posicoes[i][0], posicoes[i][1]);
            }
            return v;
        }

        /** mentalidade: profundidade média dos jogadores de linha (0 defensivo .. 1 ofensivo) */
        public float mentalidade() {
            float[][] v = vagas();
            float s = 0;
            for (int i = 1; i < 11; i++) s += v[i][0];
            return s / 10f;
        }

        /** escala o melhor time automaticamente para a formação atual */
        public void autoEscalar() {
            float[][] v = vagas();
            List<Jogador> livres = new ArrayList<>(elenco);
            titular = new int[11];
            Integer[] ordem = new Integer[11];
            for (int i = 0; i < 11; i++) ordem[i] = i;
            // goleiro primeiro, depois atacantes/defesa
            for (int i : ordem) {
                int p = (int) v[i][2];
                Jogador melhor = null;
                for (Jogador j : livres) if (melhor == null || j.ovrEm(p) > melhor.ovrEm(p)) melhor = j;
                if (melhor != null) {
                    titular[i] = melhor.id;
                    livres.remove(melhor);
                }
            }
        }

        /** garante que a escalação é válida (jogadores existem, sem repetidos) */
        public void validar() {
            boolean ok = titular != null && titular.length == 11;
            if (ok) {
                java.util.HashSet<Integer> vis = new java.util.HashSet<>();
                for (int id : titular) if (jogador(id) == null || !vis.add(id)) ok = false;
            }
            if (!ok) autoEscalar();
        }

        public List<Jogador> titulares() {
            validar();
            List<Jogador> l = new ArrayList<>();
            for (int id : titular) l.add(jogador(id));
            return l;
        }

        /** força do time: média das notas dos titulares nas suas vagas */
        public int forca() {
            validar();
            float[][] v = vagas();
            float s = 0;
            for (int i = 0; i < 11; i++) s += jogador(titular[i]).ovrEm((int) v[i][2]);
            return Math.round(s / 11);
        }

        public long folhaSemanal() {
            long s = 0;
            for (Jogador j : elenco) s += j.salario();
            return s;
        }

        public int capacidade() {
            return CAPACIDADES[Math.max(1, Math.min(10, estadio)) - 1];
        }

        JSONObject json() throws JSONException {
            JSONArray el = new JSONArray();
            for (Jogador j : elenco) el.put(j.json());
            JSONArray t = new JSONArray();
            for (int x : titular) t.put(x);
            JSONArray po = new JSONArray();
            if (posicoes != null) for (float[] q : posicoes) po.put(new JSONArray().put(q[0]).put(q[1]));
            return new JSONObject().put("po", po).put("id", id).put("n", nome).put("s", sigla).put("uf", uf).put("pa", pais).put("d", divisao).put("r", reputacao)
                    .put("c1", cor1).put("c2", cor2).put("el", el).put("t", t).put("f", formacao).put("$", dinheiro).put("es", estadio)
                    .put("ct", ct).put("ba", base).put("pi", precoIngresso).put("u", usuario);
        }

        static Clube de(JSONObject o) {
            Clube c = new Clube();
            c.id = o.optInt("id");
            c.nome = o.optString("n");
            c.sigla = o.optString("s");
            c.uf = o.optString("uf");
            c.pais = o.optString("pa", "Brasil");
            c.divisao = o.optInt("d");
            c.reputacao = o.optInt("r");
            c.cor1 = o.optInt("c1");
            c.cor2 = o.optInt("c2");
            JSONArray el = o.optJSONArray("el");
            for (int i = 0; el != null && i < el.length(); i++) c.elenco.add(Jogador.de(el.optJSONObject(i)));
            JSONArray t = o.optJSONArray("t");
            for (int i = 0; t != null && i < 11 && i < t.length(); i++) c.titular[i] = t.optInt(i);
            c.formacao = o.optString("f", "4-4-2");
            JSONArray po = o.optJSONArray("po");
            if (po != null && po.length() == 11) {
                c.posicoes = new float[11][2];
                for (int i = 0; i < 11; i++) {
                    c.posicoes[i][0] = (float) po.optJSONArray(i).optDouble(0);
                    c.posicoes[i][1] = (float) po.optJSONArray(i).optDouble(1);
                }
            }
            c.dinheiro = o.optLong("$");
            c.estadio = o.optInt("es", 1);
            c.ct = o.optInt("ct", 1);
            c.base = o.optInt("ba", 1);
            c.precoIngresso = o.optInt("pi", 30);
            c.usuario = o.optBoolean("u");
            return c;
        }
    }

    /** função pela zona do campo (x = profundidade, y = largura) */
    public static int funcao(float x, float y) {
        boolean lado = y < 0.22f || y > 0.78f;
        if (x < 0.32f) return lado ? 2 : 1;
        if (x < 0.46f) return lado ? 2 : 3;
        if (x < 0.66f) return 4;
        return 5;
    }

    public static final int[] CAPACIDADES = {5000, 9000, 14000, 20000, 28000, 36000, 46000, 56000, 68000, 80000};

    public static long custoEstadio(int nivelAtual) {
        return (long) (1_500_000 * Math.pow(1.85, nivelAtual - 1));
    }

    public static long custoCt(int nivelAtual) {
        return (long) (800_000 * Math.pow(1.8, nivelAtual - 1));
    }

    public static long custoBase(int nivelAtual) {
        return (long) (600_000 * Math.pow(1.75, nivelAtual - 1));
    }

    public static String reais(long v) {
        if (Math.abs(v) >= 1_000_000) return String.format(new Locale("pt", "BR"), "R$ %.1f mi", v / 1e6).replace(",0 mi", " mi");
        if (Math.abs(v) >= 1000) return String.format(new Locale("pt", "BR"), "R$ %d mil", v / 1000);
        return "R$ " + v;
    }

    public static List<Jogador> ordenarElenco(List<Jogador> l) {
        List<Jogador> r = new ArrayList<>(l);
        Collections.sort(r, (a, b) -> a.pos != b.pos ? a.pos - b.pos : b.ovr() - a.ovr());
        return r;
    }
}
