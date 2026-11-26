package com.victorlanes.futebol;

import android.content.Context;

import com.victorlanes.futebol.Modelo.Clube;
import com.victorlanes.futebol.Modelo.Jogador;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** O mundo da carreira: clubes, competições, calendário, mercado e a passagem do tempo. */
public final class Mundo {

    public final Random rnd = new Random();
    public int ano = 2027, dia, userId, proxJogador = 1;
    public List<Clube> clubes = new ArrayList<>();
    public List<Comp> comps = new ArrayList<>();
    public List<Dia> calendario = new ArrayList<>();
    public List<String> noticias = new ArrayList<>();
    public List<Oferta> mercado = new ArrayList<>();
    public List<String> titulos = new ArrayList<>();
    public List<Integer> vagasCont = new ArrayList<>(), vagasMund = new ArrayList<>();
    public int dificuldade = 1; // 0 fácil, 1 normal, 2 difícil
    public int minutosPartida = 4;

    // ================================================================== estruturas

    public static final class Resultado {
        public int casa, fora, gc, gf, pc = -1, pf = -1;
        public String fase = "";
        public List<String> autores = new ArrayList<>();

        JSONObject json() throws JSONException {
            JSONArray a = new JSONArray();
            for (String s : autores) a.put(s);
            return new JSONObject().put("c", casa).put("f", fora).put("gc", gc).put("gf", gf).put("pc", pc).put("pf", pf).put("fa", fase).put("a", a);
        }

        static Resultado de(JSONObject o) {
            Resultado r = new Resultado();
            r.casa = o.optInt("c");
            r.fora = o.optInt("f");
            r.gc = o.optInt("gc");
            r.gf = o.optInt("gf");
            r.pc = o.optInt("pc", -1);
            r.pf = o.optInt("pf", -1);
            r.fase = o.optString("fa");
            JSONArray a = o.optJSONArray("a");
            for (int i = 0; a != null && i < a.length(); i++) r.autores.add(a.optString(i));
            return r;
        }

        public int vencedor() {
            if (gc != gf) return gc > gf ? casa : fora;
            if (pc >= 0) return pc > pf ? casa : fora;
            return 0;
        }
    }

    public static final class Comp {
        public String id, nome, tipo; // tipo: liga | copa
        public List<Integer> times = new ArrayList<>();
        public List<int[][]> rodadas = new ArrayList<>(); // liga
        public Map<Integer, int[]> tabela = new LinkedHashMap<>(); // J V E D GP GC
        public List<Integer> vivos = new ArrayList<>(); // copa
        public List<Resultado> resultados = new ArrayList<>();
        public int campeao;
        public boolean finalEstadual;

        public int pontos(int id) {
            int[] t = tabela.get(id);
            return t == null ? 0 : t[1] * 3 + t[2];
        }

        public List<Integer> classificacao() {
            List<Integer> l = new ArrayList<>(times);
            Collections.sort(l, (a, b) -> {
                int[] x = tabela.get(a), y = tabela.get(b);
                int d = pontos(b) - pontos(a);
                if (d != 0) return d;
                if (x[1] != y[1]) return y[1] - x[1];
                int sx = x[4] - x[5], sy = y[4] - y[5];
                if (sx != sy) return sy - sx;
                return y[4] - x[4];
            });
            return l;
        }

        void registrar(Resultado r) {
            resultados.add(r);
            if (!tipo.equals("liga") || r.fase.equals("Final")) return;
            int[] a = tabela.get(r.casa), b = tabela.get(r.fora);
            a[0]++;
            b[0]++;
            a[4] += r.gc;
            a[5] += r.gf;
            b[4] += r.gf;
            b[5] += r.gc;
            if (r.gc > r.gf) {
                a[1]++;
                b[3]++;
            } else if (r.gc < r.gf) {
                b[1]++;
                a[3]++;
            } else {
                a[2]++;
                b[2]++;
            }
        }

        JSONObject json() throws JSONException {
            JSONObject o = new JSONObject().put("id", id).put("n", nome).put("t", tipo).put("c", campeao).put("fe", finalEstadual);
            o.put("ti", arr(times)).put("vi", arr(vivos));
            JSONArray ro = new JSONArray();
            for (int[][] r : rodadas) {
                JSONArray x = new JSONArray();
                for (int[] p : r) x.put(new JSONArray().put(p[0]).put(p[1]));
                ro.put(x);
            }
            o.put("ro", ro);
            JSONObject tb = new JSONObject();
            for (Map.Entry<Integer, int[]> e : tabela.entrySet()) {
                JSONArray x = new JSONArray();
                for (int v : e.getValue()) x.put(v);
                tb.put(String.valueOf(e.getKey()), x);
            }
            o.put("tb", tb);
            JSONArray rs = new JSONArray();
            for (Resultado r : resultados) rs.put(r.json());
            o.put("rs", rs);
            return o;
        }

        static Comp de(JSONObject o) {
            Comp c = new Comp();
            c.id = o.optString("id");
            c.nome = o.optString("n");
            c.tipo = o.optString("t");
            c.campeao = o.optInt("c");
            c.finalEstadual = o.optBoolean("fe");
            c.times = lista(o.optJSONArray("ti"));
            c.vivos = lista(o.optJSONArray("vi"));
            JSONArray ro = o.optJSONArray("ro");
            for (int i = 0; ro != null && i < ro.length(); i++) {
                JSONArray x = ro.optJSONArray(i);
                int[][] r = new int[x.length()][2];
                for (int k = 0; k < x.length(); k++) {
                    r[k][0] = x.optJSONArray(k).optInt(0);
                    r[k][1] = x.optJSONArray(k).optInt(1);
                }
                c.rodadas.add(r);
            }
            JSONObject tb = o.optJSONObject("tb");
            for (int id : c.times) {
                int[] v = new int[6];
                JSONArray x = tb != null ? tb.optJSONArray(String.valueOf(id)) : null;
                for (int k = 0; x != null && k < 6; k++) v[k] = x.optInt(k);
                c.tabela.put(id, v);
            }
            JSONArray rs = o.optJSONArray("rs");
            for (int i = 0; rs != null && i < rs.length(); i++) c.resultados.add(Resultado.de(rs.optJSONObject(i)));
            return c;
        }
    }

    /** um dia do calendário: uma rodada de uma ou mais competições */
    public static final class Dia {
        public List<String> comps = new ArrayList<>();
        public List<Integer> rodadas = new ArrayList<>();
        public String rotulo = "";

        Dia add(String c, int r) {
            comps.add(c);
            rodadas.add(r);
            return this;
        }

        JSONObject json() throws JSONException {
            JSONArray c = new JSONArray();
            for (String s : comps) c.put(s);
            return new JSONObject().put("c", c).put("r", arr(rodadas)).put("l", rotulo);
        }

        static Dia de(JSONObject o) {
            Dia d = new Dia();
            JSONArray c = o.optJSONArray("c");
            for (int i = 0; c != null && i < c.length(); i++) d.comps.add(c.optString(i));
            d.rodadas = lista(o.optJSONArray("r"));
            d.rotulo = o.optString("l");
            return d;
        }
    }

    public static final class Oferta {
        public Jogador j;
        public int clube; // 0 = sem clube
        public long preco;
    }

    /** uma partida marcada (para a tela de pré-jogo) */
    public static final class Partida {
        public Comp comp;
        public int casa, fora;
        public String fase;
        public boolean mataMata;
    }

    static JSONArray arr(List<Integer> l) {
        JSONArray a = new JSONArray();
        for (int v : l) a.put(v);
        return a;
    }

    static List<Integer> lista(JSONArray a) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) l.add(a.optInt(i));
        return l;
    }

    // ================================================================== acesso

    public Clube clube(int id) {
        for (Clube c : clubes) if (c.id == id) return c;
        return null;
    }

    public Clube eu() {
        return clube(userId);
    }

    public Comp comp(String id) {
        for (Comp c : comps) if (c.id.equals(id)) return c;
        return null;
    }

    public void noticia(String s) {
        noticias.add(0, "Dia " + (dia + 1) + " · " + s);
        while (noticias.size() > 60) noticias.remove(noticias.size() - 1);
    }

    // ================================================================== salvar / carregar

    static File arquivo(Context c) {
        return new File(c.getFilesDir(), "carreira.json");
    }

    public static boolean existe(Context c) {
        return arquivo(c).exists();
    }

    public void salvar(Context c) {
        try {
            JSONObject o = new JSONObject().put("ano", ano).put("dia", dia).put("u", userId).put("pj", proxJogador).put("dif", dificuldade).put("min", minutosPartida);
            JSONArray cl = new JSONArray();
            for (Clube x : clubes) cl.put(x.json());
            o.put("clubes", cl);
            JSONArray co = new JSONArray();
            for (Comp x : comps) co.put(x.json());
            o.put("comps", co);
            JSONArray ca = new JSONArray();
            for (Dia d : calendario) ca.put(d.json());
            o.put("cal", ca);
            JSONArray no = new JSONArray();
            for (String s : noticias) no.put(s);
            o.put("not", no);
            JSONArray ti = new JSONArray();
            for (String s : titulos) ti.put(s);
            o.put("tit", ti);
            JSONArray me = new JSONArray();
            for (Oferta f : mercado) me.put(new JSONObject().put("j", f.j.json()).put("c", f.clube).put("p", f.preco));
            o.put("mer", me);
            o.put("vc", arr(vagasCont)).put("vm", arr(vagasMund));
            File tmp = new File(c.getFilesDir(), "carreira.tmp");
            Files.write(tmp.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
            tmp.renameTo(arquivo(c));
        } catch (Exception ignored) {
        }
    }

    public static Mundo carregar(Context c) {
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(arquivo(c).toPath()), StandardCharsets.UTF_8));
            Mundo m = new Mundo();
            m.ano = o.optInt("ano");
            m.dia = o.optInt("dia");
            m.userId = o.optInt("u");
            m.proxJogador = o.optInt("pj", 1);
            m.dificuldade = o.optInt("dif", 1);
            m.minutosPartida = o.optInt("min", 4);
            JSONArray a = o.getJSONArray("clubes");
            for (int i = 0; i < a.length(); i++) m.clubes.add(Clube.de(a.getJSONObject(i)));
            a = o.getJSONArray("comps");
            for (int i = 0; i < a.length(); i++) m.comps.add(Comp.de(a.getJSONObject(i)));
            a = o.getJSONArray("cal");
            for (int i = 0; i < a.length(); i++) m.calendario.add(Dia.de(a.getJSONObject(i)));
            a = o.optJSONArray("not");
            for (int i = 0; a != null && i < a.length(); i++) m.noticias.add(a.optString(i));
            a = o.optJSONArray("tit");
            for (int i = 0; a != null && i < a.length(); i++) m.titulos.add(a.optString(i));
            a = o.optJSONArray("mer");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject x = a.getJSONObject(i);
                Oferta f = new Oferta();
                f.j = Jogador.de(x.getJSONObject("j"));
                f.clube = x.optInt("c");
                f.preco = x.optLong("p");
                m.mercado.add(f);
            }
            m.vagasCont = lista(o.optJSONArray("vc"));
            m.vagasMund = lista(o.optJSONArray("vm"));
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================== geração do mundo

    public static final String[] UFS = {"RJ", "SP", "MG", "RS", "PR", "BA"};
    public static final String[] NOMES_UF = {"Rio de Janeiro", "São Paulo", "Minas Gerais", "Rio Grande do Sul", "Paraná", "Bahia"};
    static final String[][] CIDADES = {
            {"Niterói", "Petrópolis", "Campos", "Volta Redonda", "Macaé", "Nova Iguaçu", "Teresópolis", "Cabo Frio", "Resende", "Angra"},
            {"Campinas", "Santos", "Sorocaba", "Ribeirão", "Bauru", "Franca", "Marília", "Jundiaí", "Piracicaba", "Limeira"},
            {"Uberlândia", "Juiz de Fora", "Montes Claros", "Ipatinga", "Divinópolis", "Varginha", "Poços", "Uberaba", "Sete Lagoas", "Itabira"},
            {"Pelotas", "Caxias", "Passo Fundo", "Santa Maria", "Bagé", "Erechim", "Novo Hamburgo", "Lajeado", "Ijuí", "Rio Grande"},
            {"Londrina", "Maringá", "Cascavel", "Ponta Grossa", "Foz", "Guarapuava", "Toledo", "Apucarana", "Paranaguá", "Umuarama"},
            {"Feira", "Vitória da Conquista", "Ilhéus", "Itabuna", "Juazeiro", "Jequié", "Barreiras", "Camaçari", "Alagoinhas", "Porto Seguro"}};
    static final String[] PREFIXOS = {"Tigres de", "Leões de", "Águias de", "Estrela de", "União", "Real", "Independente", "Esporte Clube",
            "Atlético", "Dragões de", "Grêmio", "Falcões de", "Panteras de", "Operário", "Ferroviário", "Sociedade"};
    static final int[][] CORES = {{0xFFD32F2F, 0xFFFFFFFF}, {0xFF1565C0, 0xFFFFFFFF}, {0xFF2E7D32, 0xFFFFFFFF}, {0xFF212121, 0xFFFFFFFF},
            {0xFFFBC02D, 0xFF1B5E20}, {0xFF6A1B9A, 0xFFFFFFFF}, {0xFFEF6C00, 0xFF212121}, {0xFF00838F, 0xFFFFFFFF}, {0xFFC2185B, 0xFFFFFFFF},
            {0xFF0D47A1, 0xFFFFEB3B}, {0xFF8D1C1C, 0xFF1A237E}, {0xFF1B5E20, 0xFFD32F2F}, {0xFFFFFFFF, 0xFF212121}, {0xFF4E342E, 0xFFFFC107}};

    static final String[] NOMES_BR = {"João", "Pedro", "Lucas", "Gabriel", "Matheus", "Rafael", "Bruno", "Felipe", "Gustavo", "Thiago",
            "Diego", "Vinícius", "Caio", "André", "Rodrigo", "Leandro", "Marcos", "Daniel", "Eduardo", "Renan", "Igor", "Murilo", "Henrique",
            "Otávio", "Wesley", "Everton", "Willian", "Alan", "Danilo", "Fábio", "Luan", "Ramon", "Kauã", "Ryan", "Davi", "Arthur", "Samuel",
            "Breno", "Yuri", "Cauã", "Jefferson", "Anderson", "Robson", "Cláudio", "Hugo", "Nathan", "Enzo", "Miguel", "Heitor", "Douglas"};
    static final String[] SOBRENOMES_BR = {"Silva", "Santos", "Oliveira", "Souza", "Lima", "Pereira", "Costa", "Ferreira", "Rodrigues",
            "Almeida", "Nascimento", "Araújo", "Ribeiro", "Carvalho", "Gomes", "Martins", "Rocha", "Barbosa", "Moreira", "Teixeira", "Cardoso",
            "Mendes", "Freitas", "Pinto", "Moura", "Cavalcanti", "Dias", "Castro", "Campos", "Vieira", "Batista", "Farias", "Monteiro", "Nunes",
            "Lopes", "Fonseca", "Brito", "Andrade", "Macedo", "Queiroz"};
    static final String[] APELIDOS = {"Juninho", "Neném", "Dudu", "Tiquinho", "Pelezinho", "Zé Carlos", "Tetê", "Didi", "Bebeto", "Paulinho",
            "Marquinhos", "Biel", "Nenê", "Fofão", "Cacá", "Toninho", "Serginho", "Lelê", "Pipoca", "Canhotinha", "Xodó", "Fumaça", "Foguete", "Maestro"};
    static final String[][] NOMES_EXT = { // por região: nomes, sobrenomes
            {"Mateo", "Santiago", "Joaquín", "Nicolás", "Facundo", "Agustín", "Lautaro", "Tomás", "Emiliano", "Gonzalo", "Franco", "Iván"},
            {"González", "Rodríguez", "Fernández", "López", "Martínez", "Pérez", "Sosa", "Romero", "Álvarez", "Benítez", "Acosta", "Medina"},
            {"Harry", "Jack", "Oliver", "George", "Mason", "Callum", "Reece", "Kyle", "Jordan", "Declan", "Ollie", "Conor"},
            {"Walker", "Turner", "Hughes", "Wright", "Bennett", "Carter", "Price", "Fletcher", "Barnes", "Holloway", "Pickering", "Marsh"},
            {"Luca", "Marco", "Federico", "Alessandro", "Matteo", "Lorenzo", "Andrea", "Davide", "Simone", "Riccardo", "Giorgio", "Nicolò"},
            {"Rossi", "Esposito", "Bianchi", "Romano", "Colombo", "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti", "Mancini"},
            {"Lukas", "Leon", "Finn", "Jonas", "Niklas", "Jan", "Felix", "Tim", "Moritz", "Julian", "Kai", "Max"},
            {"Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Becker", "Hoffmann", "Krause", "Wolf", "Brandt", "Vogel", "Lehmann"},
            {"Hugo", "Louis", "Théo", "Raphaël", "Mathis", "Nolan", "Enzo", "Lucas", "Yanis", "Kylian", "Adrien", "Bastien"},
            {"Martin", "Bernard", "Dubois", "Moreau", "Laurent", "Lefèvre", "Roux", "Fournier", "Girard", "Bonnet", "Mercier", "Blanc"}};

    static final String[][] PAISES = { // país, região de nomes (índice em NOMES_EXT/2), continente (0 América, 1 Europa)
            {"Argentina", "0", "0"}, {"Uruguai", "0", "0"}, {"Colômbia", "0", "0"}, {"Chile", "0", "0"},
            {"Inglaterra", "1", "1"}, {"Itália", "2", "1"}, {"Alemanha", "3", "1"}, {"França", "4", "1"}, {"Espanha", "0", "1"}, {"Portugal", "-1", "1"}};
    static final String[][] CLUBES_EXT = {
            {"Club Atlético Río Seco", "Deportivo Las Lomas", "Racing de Villa Sol"},
            {"Club Nacional del Cerro", "Defensores de Playa Mansa"},
            {"Atlético Cumbre Alta", "Deportivo Valle Verde"},
            {"Club Cóndores del Sur", "Unión Puerto Claro", "Deportes Cordillera"},
            {"Ashford Rovers", "Kingsbury Athletic", "Hollowbay United", "Redcliffe City", "Westmoor Albion"},
            {"Sporting Valdoria", "AC Montecorvo", "Real Fiorenza"},
            {"FC Hohenfels", "Borussia Kranstadt", "Eintracht Lindau"},
            {"Olympique Saint-Lune", "AS Montclair", "Stade Bellerive"},
            {"Real Montaña CF", "Atlético Costa Brava", "Unión Sierra Dorada"},
            {"Sporting Alvorada", "Académico do Mar", "Leões do Minho"}};

    public String nomeJogador(int regiao) {
        if (regiao < 0) {
            if (rnd.nextInt(7) == 0) return APELIDOS[rnd.nextInt(APELIDOS.length)];
            return NOMES_BR[rnd.nextInt(NOMES_BR.length)] + " " + SOBRENOMES_BR[rnd.nextInt(SOBRENOMES_BR.length)];
        }
        String[] n = NOMES_EXT[regiao * 2], s = NOMES_EXT[regiao * 2 + 1];
        return n[rnd.nextInt(n.length)] + " " + s[rnd.nextInt(s.length)];
    }

    /** cria um jogador de uma posição em torno de uma nota */
    public Jogador gerarJogador(int pos, int nota, int idade, int regiao) {
        Jogador j = new Jogador();
        j.id = proxJogador++;
        j.pos = pos;
        j.idade = idade;
        j.nome = nomeJogador(regiao);
        for (int i = 0; i < 6; i++) {
            float peso = Modelo.PESOS[pos][i];
            int base = peso >= .2f ? nota + rnd.nextInt(9) - 3 : peso > 0 ? nota - 8 + rnd.nextInt(14) : 20 + rnd.nextInt(Math.max(1, nota / 2));
            if (pos != 0 && i == 5) base = 10 + rnd.nextInt(15);
            if (pos == 0 && i != 5 && i != 2) base = Math.min(base, 30 + rnd.nextInt(25));
            j.a[i] = clamp(base, 15, 99);
        }
        // ajusta para chegar perto da nota pedida
        for (int k = 0; k < 6; k++) {
            int d = nota - j.ovr();
            if (Math.abs(d) <= 1) break;
            for (int i = 0; i < 6; i++) if (Modelo.PESOS[pos][i] > 0) j.a[i] = clamp(j.a[i] + d, 15, 99);
        }
        int teto = idade <= 20 ? 14 + rnd.nextInt(16) : idade <= 23 ? 6 + rnd.nextInt(12) : idade <= 27 ? rnd.nextInt(7) : 0;
        j.pot = clamp(j.ovr() + teto, j.ovr(), 96);
        return j;
    }

    static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    static final int[] MOLDE = {0, 0, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 4, 1}; // 22 jogadores

    void gerarElenco(Clube c, int nota, int regiao) {
        c.elenco.clear();
        for (int i = 0; i < MOLDE.length; i++) {
            int titular = i < 18 ? 1 : 0;
            int n = nota + rnd.nextInt(9) - 4 - (titular == 0 ? 4 : 0) - (i % 2 == 1 ? 3 : 0);
            int idade = 18 + rnd.nextInt(16);
            c.elenco.add(gerarJogador(MOLDE[i], clamp(n, 30, 95), idade, regiao));
        }
        numerar(c);
        c.autoEscalar();
    }

    static void numerar(Clube c) {
        java.util.HashSet<Integer> usados = new java.util.HashSet<>();
        for (Jogador j : c.elenco) if (j.numero > 0 && !usados.add(j.numero)) j.numero = 0;
        int[] pref = {1, 3, 2, 5, 8, 9};
        for (Jogador j : Modelo.ordenarElenco(c.elenco)) {
            if (j.numero > 0) continue;
            int n = pref[j.pos];
            if (usados.contains(n)) {
                n = 2;
                while (usados.contains(n)) n++;
            }
            j.numero = n;
            usados.add(n);
        }
    }

    /** cria uma carreira nova com o clube do usuário */
    public static Mundo novo(String nome, String sigla, String uf, int cor1, int cor2) {
        Mundo m = new Mundo();
        int id = 1;
        // brasileiros: 6 estados x 8 clubes
        String[][] padroes = {{"A", "A", "A", "B", "B", "C", "C", "C"}, {"A", "A", "B", "B", "B", "B", "C", "C"}};
        for (int e = 0; e < UFS.length; e++) {
            List<String> cidades = new ArrayList<>(java.util.Arrays.asList(CIDADES[e]));
            Collections.shuffle(cidades, m.rnd);
            String[] p = padroes[e < 4 ? 0 : 1];
            for (int k = 0; k < 8; k++) {
                Clube c = new Clube();
                c.id = id++;
                c.uf = UFS[e];
                String cid = cidades.get(k);
                c.nome = PREFIXOS[m.rnd.nextInt(PREFIXOS.length)] + " " + cid;
                c.sigla = sigla(cid);
                int[] cs = CORES[m.rnd.nextInt(CORES.length)];
                c.cor1 = cs[0];
                c.cor2 = cs[1];
                c.divisao = p[k].equals("A") ? 1 : p[k].equals("B") ? 2 : 3;
                int nota = c.divisao == 1 ? 68 + m.rnd.nextInt(8) : c.divisao == 2 ? 60 + m.rnd.nextInt(7) : 52 + m.rnd.nextInt(7);
                c.reputacao = c.divisao == 1 ? 60 + m.rnd.nextInt(30) : c.divisao == 2 ? 35 + m.rnd.nextInt(20) : 15 + m.rnd.nextInt(15);
                c.estadio = c.divisao == 1 ? 6 + m.rnd.nextInt(4) : c.divisao == 2 ? 3 + m.rnd.nextInt(3) : 1 + m.rnd.nextInt(2);
                c.formacao = Modelo.FORMACOES[m.rnd.nextInt(4)];
                m.gerarElenco(c, nota, -1);
                m.clubes.add(c);
            }
        }
        // o clube do usuário ocupa a vaga do pior clube da Série C do seu estado
        Clube pior = null;
        for (Clube c : m.clubes) if (c.uf.equals(uf) && c.divisao == 3 && (pior == null || c.forca() < pior.forca())) pior = c;
        pior.nome = nome;
        pior.sigla = sigla;
        pior.cor1 = cor1;
        pior.cor2 = cor2;
        pior.usuario = true;
        pior.reputacao = 15;
        pior.estadio = 1;
        pior.ct = 1;
        pior.base = 1;
        pior.dinheiro = 3_000_000;
        pior.formacao = "4-4-2";
        m.gerarElenco(pior, 54, -1);
        m.userId = pior.id;

        // estrangeiros
        for (int p = 0; p < PAISES.length; p++) {
            for (String n : CLUBES_EXT[p]) {
                Clube c = new Clube();
                c.id = id++;
                c.pais = PAISES[p][0];
                c.nome = n;
                c.sigla = sigla(n.replaceAll("^(Club|Deportivo|Racing de|Atlético|Unión|Real|Sporting|AC|FC|Borussia|Eintracht|Olympique|AS|Stade|Académico do|Leões do|Defensores de|Deportes|Club Nacional del|Club Cóndores del) ", ""));
                int[] cs = CORES[m.rnd.nextInt(CORES.length)];
                c.cor1 = cs[0];
                c.cor2 = cs[1];
                boolean europa = PAISES[p][2].equals("1");
                c.divisao = europa ? 10 : 9;
                int nota = europa ? 74 + m.rnd.nextInt(11) : 66 + m.rnd.nextInt(9);
                c.reputacao = europa ? 85 + m.rnd.nextInt(15) : 60 + m.rnd.nextInt(25);
                c.estadio = europa ? 8 + m.rnd.nextInt(3) : 5 + m.rnd.nextInt(4);
                c.formacao = Modelo.FORMACOES[m.rnd.nextInt(4)];
                m.gerarElenco(c, nota, Integer.parseInt(PAISES[p][1]));
                m.clubes.add(c);
            }
        }
        m.iniciarTemporada(true);
        m.renovarMercado();
        m.noticia("Bem-vindo, presidente! O " + nome + " começa na Série C. Monte o elenco e construa o clube!");
        return m;
    }

    static String sigla(String s) {
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z ]", "").trim().toUpperCase();
        String[] p = t.split("\\s+");
        if (p.length >= 2 && p[0].length() > 2) return (p[0].substring(0, 2) + p[1].charAt(0));
        return (t.replace(" ", "") + "XXX").substring(0, 3);
    }

    // ================================================================== temporada

    List<Integer> idsDivisao(int d) {
        List<Integer> l = new ArrayList<>();
        for (Clube c : clubes) if (c.divisao == d) l.add(c.id);
        return l;
    }

    static List<int[][]> roundRobin(List<Integer> times, Random rnd) {
        List<Integer> t = new ArrayList<>(times);
        Collections.shuffle(t, rnd);
        if (t.size() % 2 == 1) t.add(-1);
        int n = t.size();
        List<int[][]> rodadas = new ArrayList<>();
        for (int r = 0; r < n - 1; r++) {
            List<int[]> jogos = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                int a = t.get(i), b = t.get(n - 1 - i);
                if (a < 0 || b < 0) continue;
                jogos.add(r % 2 == 0 ? new int[]{a, b} : new int[]{b, a});
            }
            rodadas.add(jogos.toArray(new int[0][]));
            t.add(1, t.remove(n - 1));
        }
        return rodadas;
    }

    Comp novaLiga(String id, String nome, List<Integer> times) {
        Comp c = new Comp();
        c.id = id;
        c.nome = nome;
        c.tipo = "liga";
        c.times = new ArrayList<>(times);
        for (int t : times) c.tabela.put(t, new int[6]);
        c.rodadas = roundRobin(times, rnd);
        return c;
    }

    Comp novaCopa(String id, String nome, List<Integer> times) {
        Comp c = new Comp();
        c.id = id;
        c.nome = nome;
        c.tipo = "copa";
        c.times = new ArrayList<>(times);
        c.vivos = new ArrayList<>(times);
        return c;
    }

    List<Integer> melhores(List<Integer> ids, int n) {
        List<Integer> l = new ArrayList<>(ids);
        Collections.sort(l, (a, b) -> clube(b).forca() - clube(a).forca());
        return new ArrayList<>(l.subList(0, Math.min(n, l.size())));
    }

    List<Integer> estrangeiros(boolean europa) {
        List<Integer> l = new ArrayList<>();
        for (Clube c : clubes) if (c.divisao == (europa ? 10 : 9)) l.add(c.id);
        return l;
    }

    void iniciarTemporada(boolean primeira) {
        comps.clear();
        calendario.clear();
        dia = 0;
        for (Clube c : clubes) for (Jogador j : c.elenco) {
            j.gols = 0;
            j.jogos = 0;
            j.assist = 0;
        }
        for (String uf : UFS) {
            List<Integer> l = new ArrayList<>();
            for (Clube c : clubes) if (uf.equals(c.uf)) l.add(c.id);
            comps.add(novaLiga("est_" + uf, "Estadual " + NOMES_UF[java.util.Arrays.asList(UFS).indexOf(uf)], l));
        }
        comps.add(novaLiga("serieA", "Liga Nacional · Série A", idsDivisao(1)));
        comps.add(novaLiga("serieB", "Liga Nacional · Série B", idsDivisao(2)));
        comps.add(novaLiga("serieC", "Liga Nacional · Série C", idsDivisao(3)));
        List<Integer> copa = new ArrayList<>(idsDivisao(1));
        copa.addAll(idsDivisao(2));
        copa.addAll(idsDivisao(3));
        comps.add(novaCopa("copa", "Copa Nacional", copa));

        if (primeira || vagasCont.isEmpty()) vagasCont = melhores(idsDivisao(1), 4);
        List<Integer> cont = new ArrayList<>(vagasCont);
        for (int id : melhores(estrangeiros(false), 16 - cont.size())) if (!cont.contains(id)) cont.add(id);
        for (int id : melhores(idsDivisao(1), 16)) if (cont.size() < 16 && !cont.contains(id)) cont.add(id);
        while (cont.size() > 16) cont.remove(cont.size() - 1);
        comps.add(novaCopa("cont", "Copa das Américas de Clubes", cont));

        if (primeira || vagasMund.isEmpty()) {
            vagasMund = melhores(idsDivisao(1), 1);
            vagasMund.add(melhores(estrangeiros(false), 1).get(0));
        }
        List<Integer> mund = new ArrayList<>(vagasMund);
        for (int id : melhores(estrangeiros(true), 16)) if (mund.size() < 16 && !mund.contains(id)) mund.add(id);
        comps.add(novaCopa("mund", "Copa dos Campeões Mundial", mund));

        // calendário
        for (int r = 0; r < 7; r++) {
            Dia d = new Dia();
            d.rotulo = "Estaduais · Rodada " + (r + 1);
            for (String uf : UFS) d.add("est_" + uf, r);
            calendario.add(d);
        }
        Dia fin = new Dia();
        fin.rotulo = "Estaduais · Finais";
        for (String uf : UFS) fin.add("est_" + uf, 7);
        calendario.add(fin);
        String[] fasesCopa = {"Primeira fase", "16 avos de final", "Oitavas de final", "Quartas de final", "Semifinal", "Final"};
        String[] fasesInt = {"Oitavas de final", "Quartas de final", "Semifinal", "Final"};
        int copaF = 0, contF = 0, mundF = 0;
        for (int r = 0; r < 15; r++) {
            Dia d = new Dia();
            d.rotulo = "Liga Nacional · Rodada " + (r + 1);
            d.add("serieA", r).add("serieB", r).add("serieC", r);
            calendario.add(d);
            if (r % 2 == 1 && copaF < 6) {
                Dia c = new Dia();
                c.rotulo = "Copa Nacional · " + fasesCopa[copaF];
                c.add("copa", copaF++);
                calendario.add(c);
            }
            if ((r == 2 || r == 6 || r == 10 || r == 14) && contF < 4) {
                Dia c = new Dia();
                c.rotulo = "Copa das Américas · " + fasesInt[contF];
                c.add("cont", contF++);
                calendario.add(c);
            }
            if ((r == 4 || r == 8 || r == 12) && mundF < 4) {
                Dia c = new Dia();
                c.rotulo = "Copa dos Campeões · " + fasesInt[mundF];
                c.add("mund", mundF++);
                calendario.add(c);
            }
        }
        while (copaF < 6) {
            Dia c = new Dia();
            c.rotulo = "Copa Nacional · " + fasesCopa[copaF];
            c.add("copa", copaF++);
            calendario.add(c);
        }
        Dia fm = new Dia();
        fm.rotulo = "Copa dos Campeões · Final";
        fm.add("mund", mundF);
        calendario.add(fm);
    }

    public boolean fimDeTemporada() {
        return dia >= calendario.size();
    }

    /** jogos do dia atual: liga = rodada; copa = sorteio da fase com os vivos */
    public List<Partida> jogosDoDia() {
        List<Partida> l = new ArrayList<>();
        if (fimDeTemporada()) return l;
        Dia d = calendario.get(dia);
        for (int i = 0; i < d.comps.size(); i++) {
            Comp c = comp(d.comps.get(i));
            int r = d.rodadas.get(i);
            if (c == null) continue;
            if (c.tipo.equals("liga")) {
                if (r < c.rodadas.size()) {
                    for (int[] p : c.rodadas.get(r)) l.add(partida(c, p[0], p[1], "Rodada " + (r + 1), false));
                } else { // final do estadual: 1º x 2º
                    List<Integer> cl = c.classificacao();
                    l.add(partida(c, cl.get(0), cl.get(1), "Final", true));
                }
            } else {
                List<Integer> v = new ArrayList<>(c.vivos);
                String fase = d.rotulo.substring(d.rotulo.indexOf("·") + 2);
                if (c.id.equals("copa") && r == 0) { // Série B x Série C
                    List<Integer> b = new ArrayList<>(), cc = new ArrayList<>();
                    for (int id : v) {
                        int dv = clube(id).divisao;
                        if (dv == 2) b.add(id);
                        else if (dv == 3) cc.add(id);
                    }
                    Collections.shuffle(b, rnd);
                    Collections.shuffle(cc, rnd);
                    for (int k = 0; k < Math.min(b.size(), cc.size()); k++) l.add(partida(c, cc.get(k), b.get(k), fase, true));
                } else {
                    Collections.shuffle(v, rnd);
                    for (int k = 0; k + 1 < v.size(); k += 2) l.add(partida(c, v.get(k), v.get(k + 1), fase, true));
                }
            }
        }
        return l;
    }

    Partida partida(Comp c, int casa, int fora, String fase, boolean mm) {
        Partida p = new Partida();
        p.comp = c;
        p.casa = casa;
        p.fora = fora;
        p.fase = fase;
        p.mataMata = mm;
        return p;
    }

    // ================================================================== simulação

    double poisson(double l) {
        double L = Math.exp(-l), p = 1;
        int k = 0;
        do {
            k++;
            p *= rnd.nextDouble();
        } while (p > L);
        return k - 1;
    }

    public Resultado simular(Partida p) {
        Clube a = clube(p.casa), b = clube(p.fora);
        double fa = a.forca(), fb = b.forca();
        Resultado r = new Resultado();
        r.casa = p.casa;
        r.fora = p.fora;
        r.fase = p.fase;
        r.gc = (int) Math.min(7, poisson(Math.min(4.5, 1.35 * Math.exp((fa - fb + 2) / 13))));
        r.gf = (int) Math.min(7, poisson(Math.min(4.5, 1.1 * Math.exp((fb - fa - 2) / 13))));
        for (int i = 0; i < r.gc; i++) r.autores.add(autor(a, true));
        for (int i = 0; i < r.gf; i++) r.autores.add(autor(b, false));
        if (p.mataMata && r.gc == r.gf) penaltis(r, fa, fb);
        return r;
    }

    public void penaltis(Resultado r, double fa, double fb) {
        int pa = 0, pb = 0;
        for (int i = 0; i < 5; i++) {
            if (rnd.nextDouble() < 0.72 + (fa - fb) / 300) pa++;
            if (rnd.nextDouble() < 0.72 + (fb - fa) / 300) pb++;
        }
        while (pa == pb) {
            if (rnd.nextDouble() < 0.7) pa++;
            if (rnd.nextDouble() < 0.7) pb++;
        }
        r.pc = pa;
        r.pf = pb;
    }

    String autor(Clube c, boolean casa) {
        float[] peso = {0, .7f, 1, 1.5f, 3, 5};
        List<Jogador> t = c.titulares();
        float tot = 0;
        for (Jogador j : t) tot += peso[j.pos] * (0.5f + j.a[1] / 100f);
        float x = rnd.nextFloat() * tot;
        for (Jogador j : t) {
            x -= peso[j.pos] * (0.5f + j.a[1] / 100f);
            if (x <= 0) {
                j.gols++;
                j.golsCarreira++;
                return (casa ? "C" : "F") + j.nome;
            }
        }
        return casa ? "C" : "F";
    }

    /** registra um resultado (simulado ou jogado) e atualiza a copa */
    public void registrar(Partida p, Resultado r) {
        p.comp.registrar(r);
        for (int id : new int[]{r.casa, r.fora}) for (Jogador j : clube(id).titulares()) {
            j.jogos++;
            j.jogosCarreira++;
        }
        if (p.mataMata) {
            int perdedor = r.vencedor() == r.casa ? r.fora : r.casa;
            if (p.comp.tipo.equals("copa")) p.comp.vivos.remove(Integer.valueOf(perdedor));
            if (p.fase.equals("Final")) p.comp.campeao = r.vencedor();
        }
        boolean eu = r.casa == userId || r.fora == userId;
        if (eu) {
            Clube u = eu();
            int v = r.vencedor();
            boolean ganhou = v == userId, empatou = v == 0;
            u.reputacao = clamp(u.reputacao + (ganhou ? 1 : empatou ? 0 : -1), 1, 100);
            if (r.casa == userId) bilheteria(u, p);
            premio(p, ganhou, empatou);
        }
    }

    public int ocupacao(Clube u) {
        double base = 0.25 + u.reputacao / 160.0 + (u.divisao == 1 ? .15 : u.divisao == 2 ? .07 : 0);
        double preco = Math.pow(30.0 / Math.max(5, u.precoIngresso), 0.6);
        return (int) Math.round(Math.max(5, Math.min(100, base * preco * 100)));
    }

    void bilheteria(Clube u, Partida p) {
        double fator = p.comp.id.equals("mund") ? 2.5 : p.comp.id.equals("cont") ? 1.8 : p.mataMata ? 1.3 : 1;
        long publico = Math.round(u.capacidade() * ocupacao(u) / 100.0 * Math.min(1, fator));
        long renda = Math.round(publico * u.precoIngresso * fator);
        u.dinheiro += renda;
        noticia("🏟️ Público de " + publico + " torcedores · bilheteria " + Modelo.reais(renda));
    }

    void premio(Partida p, boolean ganhou, boolean empatou) {
        Clube u = eu();
        long base = p.comp.id.equals("mund") ? 3_000_000 : p.comp.id.equals("cont") ? 1_200_000 : p.comp.id.equals("copa") ? 400_000
                : p.comp.id.equals("serieA") ? 500_000 : p.comp.id.equals("serieB") ? 200_000 : p.comp.id.startsWith("est_") ? 60_000 : 90_000;
        long v = ganhou ? base : empatou ? base / 3 : 0;
        if (v > 0) u.dinheiro += v;
    }

    /** acontece todo dia: salários, patrocínio, treino */
    public void rotinaDiaria() {
        Clube u = eu();
        long patrocinio = (u.divisao == 1 ? 650_000 : u.divisao == 2 ? 260_000 : 110_000) + u.reputacao * 4000L;
        long manutencao = (u.estadio + u.ct + u.base) * 12_000L;
        u.dinheiro += patrocinio - u.folhaSemanal() - manutencao;
        for (Clube c : clubes) treinar(c, c.usuario ? u.ct : 3 + (c.divisao == 10 ? 3 : c.divisao <= 1 ? 2 : 0));
        if (dia % 4 == 0) renovarMercado();
    }

    void treinar(Clube c, int nivelCt) {
        for (Jogador j : c.elenco) {
            int ovr = j.ovr();
            if (j.pot <= ovr) continue;
            float idadeF = j.idade <= 20 ? 1.4f : j.idade <= 24 ? 1f : j.idade <= 28 ? .5f : .2f;
            j.evolucao += (0.06f + nivelCt * 0.03f) * idadeF * Math.min(1f, (j.pot - ovr) / 6f);
            while (j.evolucao >= 1) {
                j.evolucao -= 1;
                melhorar(j);
            }
        }
    }

    void melhorar(Jogador j) {
        float[] p = Modelo.PESOS[j.pos];
        float tot = 0;
        for (float x : p) tot += x;
        float x = rnd.nextFloat() * tot;
        for (int i = 0; i < 6; i++) {
            x -= p[i];
            if (x <= 0) {
                j.a[i] = Math.min(99, j.a[i] + 1);
                return;
            }
        }
    }

    // ================================================================== mercado

    public void renovarMercado() {
        mercado.clear();
        List<Clube> outros = new ArrayList<>();
        for (Clube c : clubes) if (!c.usuario) outros.add(c);
        for (int i = 0; i < 34; i++) {
            Clube c = outros.get(rnd.nextInt(outros.size()));
            if (c.elenco.size() <= 18) continue;
            Jogador j = c.elenco.get(rnd.nextInt(c.elenco.size()));
            boolean repetido = false;
            for (Oferta o : mercado) if (o.j.id == j.id) repetido = true;
            if (repetido) continue;
            Oferta o = new Oferta();
            o.j = j;
            o.clube = c.id;
            o.preco = Math.round(j.valor() * (1.05 + rnd.nextDouble() * 0.35) / 1000) * 1000;
            mercado.add(o);
        }
        for (int i = 0; i < 6; i++) { // sem clube (veteranos e jovens)
            Oferta o = new Oferta();
            boolean jovem = rnd.nextBoolean();
            o.j = gerarJogador(rnd.nextInt(6), jovem ? 45 + rnd.nextInt(10) : 55 + rnd.nextInt(12), jovem ? 17 + rnd.nextInt(3) : 31 + rnd.nextInt(4), -1);
            o.clube = 0;
            o.preco = Math.round(o.j.valor() * 0.3 / 1000) * 1000;
            mercado.add(o);
        }
        Collections.sort(mercado, (a, b) -> b.j.ovr() - a.j.ovr());
    }

    public String comprar(Oferta o) {
        Clube u = eu();
        if (u.elenco.size() >= 32) return "Elenco cheio (máximo 32 jogadores). Venda alguém antes.";
        if (u.dinheiro < o.preco) return "Dinheiro insuficiente.";
        Clube origem = clube(o.clube);
        if (origem != null) {
            if (origem.jogador(o.j.id) == null) return "Esse jogador não está mais disponível.";
            origem.elenco.remove(o.j);
            origem.dinheiro += o.preco;
            // o clube vendedor repõe a vaga
            origem.elenco.add(gerarJogador(o.j.pos, Math.max(40, o.j.ovr() - 6), 18 + rnd.nextInt(5), origem.divisao >= 9 ? -1 : -1));
            numerar(origem);
            origem.validar();
        }
        u.dinheiro -= o.preco;
        o.j.numero = 0;
        u.elenco.add(o.j);
        numerar(u);
        mercado.remove(o);
        noticia("✍️ Contratado: " + o.j.nome + " (" + Modelo.POS[o.j.pos] + ", " + o.j.ovr() + ") por " + Modelo.reais(o.preco));
        return null;
    }

    /** proposta de um clube da IA por um jogador do usuário */
    public long proposta(Jogador j) {
        return Math.round(j.valor() * (0.75 + rnd.nextDouble() * 0.35) / 1000) * 1000;
    }

    public String vender(Jogador j, long valor) {
        Clube u = eu();
        if (u.elenco.size() <= 16) return "O elenco precisa de pelo menos 16 jogadores.";
        List<Clube> compradores = new ArrayList<>();
        for (Clube c : clubes) if (!c.usuario && c.elenco.size() < 30 && c.forca() >= j.ovr() - 12) compradores.add(c);
        Clube c = compradores.isEmpty() ? clubes.get(rnd.nextInt(clubes.size())) : compradores.get(rnd.nextInt(compradores.size()));
        u.elenco.remove(j);
        u.dinheiro += valor;
        j.numero = 0;
        c.elenco.add(j);
        numerar(c);
        u.validar();
        noticia("💰 " + j.nome + " vendido ao " + c.nome + " por " + Modelo.reais(valor));
        return null;
    }

    // ================================================================== fim de temporada

    public List<String> encerrarTemporada() {
        List<String> resumo = new ArrayList<>();
        Clube u = eu();
        // títulos
        for (Comp c : comps) {
            int camp = c.tipo.equals("liga") && !c.id.startsWith("est_") ? c.classificacao().get(0) : c.campeao;
            if (c.tipo.equals("liga") && !c.id.startsWith("est_")) c.campeao = camp;
            if (camp == userId) {
                titulos.add(ano + " · " + c.nome);
                resumo.add("🏆 Campeão: " + c.nome + "!");
                u.reputacao = clamp(u.reputacao + 8, 1, 100);
                long p = c.id.equals("mund") ? 40_000_000 : c.id.equals("cont") ? 15_000_000 : c.id.equals("serieA") ? 20_000_000
                        : c.id.equals("copa") ? 8_000_000 : c.id.equals("serieB") ? 5_000_000 : c.id.equals("serieC") ? 2_000_000 : 800_000;
                u.dinheiro += p;
                resumo.add("   Prêmio: " + Modelo.reais(p));
            }
        }
        // acesso e rebaixamento
        List<Integer> a = comp("serieA").classificacao(), b = comp("serieB").classificacao(), c = comp("serieC").classificacao();
        int nA = a.size(), nB = b.size();
        for (int i = 0; i < 3; i++) {
            clube(a.get(nA - 1 - i)).divisao = 2;
            clube(b.get(i)).divisao = 1;
            clube(b.get(nB - 1 - i)).divisao = 3;
            clube(c.get(i)).divisao = 2;
        }
        Comp minhaLiga = comp(u.divisao == 1 ? "serieA" : u.divisao == 2 ? "serieB" : "serieC");
        int pos = minhaLiga.classificacao().indexOf(userId) + 1;
        resumo.add("📊 " + minhaLiga.nome + ": " + pos + "º lugar");
        if (a.subList(nA - 3, nA).contains(userId) || b.subList(nB - 3, nB).contains(userId)) resumo.add("⬇️ Rebaixado para a Série " + (u.divisao == 2 ? "B" : "C"));
        if (b.subList(0, 3).contains(userId) || c.subList(0, 3).contains(userId)) resumo.add("⬆️ ACESSO! Próxima temporada na Série " + (u.divisao == 1 ? "A" : "B"));
        // vagas internacionais
        vagasCont = new ArrayList<>(a.subList(0, 4));
        vagasMund = new ArrayList<>();
        vagasMund.add(a.get(0));
        Comp cont = comp("cont");
        if (cont.campeao != 0 && !vagasMund.contains(cont.campeao)) vagasMund.add(cont.campeao);
        if (vagasCont.contains(userId)) resumo.add("🌎 Classificado para a Copa das Américas!");
        if (vagasMund.contains(userId)) resumo.add("🌍 Classificado para a Copa dos Campeões Mundial!");
        // envelhecimento, aposentadorias e base
        for (Clube cl : clubes) {
            List<Jogador> sair = new ArrayList<>();
            for (Jogador j : cl.elenco) {
                j.idade++;
                if (j.idade >= 31) for (int i = 0; i < 6; i++) if (Modelo.PESOS[j.pos][i] > 0 && rnd.nextInt(3) > 0) j.a[i] = Math.max(15, j.a[i] - (j.idade >= 34 ? 3 : 1) - rnd.nextInt(2));
                if (j.idade >= 36 || (j.idade >= 34 && rnd.nextInt(3) == 0)) sair.add(j);
            }
            for (Jogador j : sair) {
                cl.elenco.remove(j);
                if (cl.usuario) resumo.add("👋 " + j.nome + " se aposentou aos " + j.idade + " anos");
                if (!cl.usuario || cl.elenco.size() < 18) {
                    int nota = cl.usuario ? 42 + cl.base * 3 : Math.max(40, cl.forca() - 8);
                    cl.elenco.add(gerarJogador(j.pos, nota, 18 + rnd.nextInt(3), cl.divisao == 10 ? 1 : -1));
                }
            }
            numerar(cl);
            cl.validar();
        }
        // revelações da base do usuário
        for (int i = 0; i < 2; i++) {
            Jogador j = gerarJogador(rnd.nextInt(5) + 1, 40 + u.base * 3 + rnd.nextInt(6), 16 + rnd.nextInt(2), -1);
            j.pot = clamp(j.ovr() + 15 + u.base * 2 + rnd.nextInt(10), j.ovr(), 95);
            u.elenco.add(j);
            resumo.add("🌱 Revelação da base: " + j.nome + " (" + Modelo.POS[j.pos] + ", " + j.ovr() + " → potencial " + j.pot + ")");
        }
        numerar(u);
        ano++;
        iniciarTemporada(false);
        renovarMercado();
        noticia("Começa a temporada " + ano + "!");
        return resumo;
    }

    /** força média para exibir a divisão de um clube */
    public static String nomeDivisao(Clube c) {
        if (c.divisao == 1) return "Série A";
        if (c.divisao == 2) return "Série B";
        if (c.divisao == 3) return "Série C";
        return c.pais;
    }

    // jogos do dia ficam fixos depois de sorteados (não são salvos: se o app fechar, sorteia de novo)
    private List<Partida> hoje;
    private int diaHoje = -1;

    public List<Partida> jogosHoje() {
        if (hoje == null || diaHoje != dia || ano != anoHoje) {
            hoje = jogosDoDia();
            diaHoje = dia;
            anoHoje = ano;
        }
        return hoje;
    }

    private int anoHoje;

    public Partida meuJogo() {
        for (Partida p : jogosHoje()) if (p.casa == userId || p.fora == userId) return p;
        return null;
    }

    /** simula o restante do dia (o jogo do usuário já pode ter sido registrado) e avança */
    public List<Resultado> concluirDia(Resultado meu) {
        List<Resultado> rs = new ArrayList<>();
        for (Partida p : jogosHoje()) {
            boolean eu = p.casa == userId || p.fora == userId;
            Resultado r = eu ? meu : simular(p);
            if (r == null) r = simular(p);
            registrar(p, r);
            rs.add(r);
        }
        rotinaDiaria();
        dia++;
        hoje = null;
        return rs;
    }

    public List<Jogador[]> artilheiros(Comp c, int n) {
        List<Jogador[]> l = new ArrayList<>();
        List<Jogador> todos = new ArrayList<>();
        Map<Integer, Clube> dono = new HashMap<>();
        for (int id : c.times) {
            Clube cl = clube(id);
            if (cl == null) continue;
            for (Jogador j : cl.elenco) if (j.gols > 0) {
                todos.add(j);
                dono.put(j.id, cl);
            }
        }
        Collections.sort(todos, (a, b) -> b.gols - a.gols);
        for (int i = 0; i < Math.min(n, todos.size()); i++) l.add(new Jogador[]{todos.get(i)});
        return l;
    }

    public Clube clubeDoJogador(int jogadorId) {
        for (Clube c : clubes) if (c.jogador(jogadorId) != null) return c;
        return null;
    }
}
