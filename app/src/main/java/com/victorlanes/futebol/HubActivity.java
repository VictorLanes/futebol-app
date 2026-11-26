package com.victorlanes.futebol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.victorlanes.futebol.Modelo.Clube;
import com.victorlanes.futebol.Modelo.Jogador;
import com.victorlanes.futebol.Mundo.Comp;
import com.victorlanes.futebol.Mundo.Partida;
import com.victorlanes.futebol.Mundo.Resultado;

import java.util.ArrayList;
import java.util.List;

/** Central do técnico/presidente: agenda, elenco, mercado, clube e torneios. */
public class HubActivity extends Activity {

    private static final int REQ_JOGO = 5;
    private static final String[] ABAS = {"inicio", "elenco", "mercado", "clube", "torneios"};
    private static final String[] NOMES = {"Início", "Elenco", "Mercado", "Clube", "Torneios"};
    private static final int[] ICONES = {R.drawable.ic_inicio, R.drawable.ic_elenco, R.drawable.ic_mercado, R.drawable.ic_clube, R.drawable.ic_torneios};

    private Ui.Cores k;
    private Mundo m;
    private String aba = "inicio";
    private FrameLayout conteudo;
    private LinearLayout nav;
    private String compSel, filtroPos = "todos";
    private Partida jogoAtual;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        k = Ui.cores(this);
        m = Sessao.mundo(this);
        if (m == null) {
            finish();
            return;
        }
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        LinearLayout raiz = Ui.coluna(this);
        raiz.setBackgroundColor(k.fundo);
        setContentView(raiz);
        Ui.aplicarInsets(raiz);
        conteudo = new FrameLayout(this);
        raiz.addView(conteudo, Ui.lp(-1, 0, 1f));
        nav = Ui.linha(this);
        nav.setBackgroundColor(k.superficie);
        nav.setPadding(dp(4), dp(8), dp(4), dp(10));
        nav.setElevation(dp(8));
        raiz.addView(nav, Ui.lp(-1, -2));
        mostrar();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void montarNav() {
        nav.removeAllViews();
        for (int i = 0; i < ABAS.length; i++) {
            String id = ABAS[i];
            boolean on = id.equals(aba);
            LinearLayout it = Ui.coluna(this);
            it.setGravity(Gravity.CENTER);
            FrameLayout pil = new FrameLayout(this);
            pil.setBackground(on ? Ui.fundo(k.chipSel, dp(16)) : null);
            android.widget.ImageView ic = new android.widget.ImageView(this);
            ic.setImageResource(ICONES[i]);
            ic.setColorFilter(on ? 0xFF4ADE80 : 0xFFC9D8CE);
            pil.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            it.addView(pil, Ui.lp(dp(60), dp(32)));
            TextView nt = Ui.texto(this, NOMES[i], 12, on ? 0xFFFFFFFF : 0xFFC9D8CE, on);
            nt.setGravity(Gravity.CENTER);
            nt.setPadding(0, dp(3), 0, 0);
            it.addView(nt, Ui.lp(-1, -2));
            it.setOnClickListener(v -> {
                aba = id;
                mostrar();
            });
            nav.addView(it, Ui.lp(0, -2, 1f));
        }
    }

    private void mostrar() {
        montarNav();
        conteudo.removeAllViews();
        switch (aba) {
            case "elenco":
                abaElenco();
                break;
            case "mercado":
                abaMercado();
                break;
            case "clube":
                abaClube();
                break;
            case "torneios":
                abaTorneios();
                break;
            default:
                abaInicio();
        }
    }

    private LinearLayout pagina() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.coluna(this);
        col.setPadding(dp(14), dp(12), dp(14), dp(24));
        sv.addView(col);
        conteudo.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        // cabeçalho do clube
        Clube eu = m.eu();
        LinearLayout cab = Ui.linha(this);
        cab.setGravity(Gravity.CENTER_VERTICAL);
        cab.addView(new Escudo(this).de(eu), Ui.lp(dp(42), dp(48)));
        LinearLayout t = Ui.coluna(this);
        t.setPadding(dp(10), 0, 0, 0);
        TextView n = Ui.texto(this, eu.nome, 18, k.texto, true);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);
        t.addView(Ui.texto(this, "Temporada " + m.ano + " · " + Mundo.nomeDivisao(eu) + " · Força " + eu.forca(), 12, k.sub, false));
        cab.addView(t, Ui.lp(0, -2, 1f));
        TextView din = Ui.texto(this, Modelo.reais(eu.dinheiro), 15, eu.dinheiro < 0 ? 0xFFEF4444 : k.destaque, true);
        cab.addView(din);
        TextView menu = Ui.texto(this, "⋮", 24, k.texto, true);
        menu.setPadding(dp(12), 0, dp(4), 0);
        menu.setOnClickListener(v -> ajustes());
        cab.addView(menu);
        col.addView(cab);
        return col;
    }

    private LinearLayout cartao(LinearLayout col) {
        LinearLayout c = Ui.coluna(this);
        c.setBackground(Ui.fundo(k.superficie, dp(20)));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = dp(12);
        col.addView(c, lp);
        return c;
    }

    private TextView titulo(LinearLayout c, String s) {
        TextView t = Ui.texto(this, s, 16, k.texto, true);
        c.addView(t);
        return t;
    }

    // ================================================================== INÍCIO

    private void abaInicio() {
        LinearLayout col = pagina();
        Clube eu = m.eu();
        if (m.fimDeTemporada()) {
            LinearLayout c = cartao(col);
            titulo(c, "🏁 Fim da temporada " + m.ano);
            c.addView(Ui.texto(this, "Veja acessos, rebaixamentos, títulos e as revelações da base.", 14, k.sub, false));
            Button b = Ui.botaoGradiente(this, "Encerrar temporada");
            b.setOnClickListener(v -> {
                List<String> r = m.encerrarTemporada();
                Sessao.salvar(this);
                new AlertDialog.Builder(this).setTitle("Resumo da temporada").setMessage(TextUtils.join("\n", r))
                        .setPositiveButton("Começar " + m.ano, null).show();
                mostrar();
            });
            LinearLayout.LayoutParams lp = Ui.lp(-1, dp(54));
            lp.topMargin = dp(12);
            c.addView(b, lp);
        } else {
            Mundo.Dia dia = m.calendario.get(m.dia);
            Partida p = m.meuJogo();
            LinearLayout c = Ui.coluna(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF14532D, 0xFF0F3D2E});
            g.setCornerRadius(dp(24));
            c.setBackground(g);
            c.setPadding(dp(16), dp(14), dp(16), dp(16));
            LinearLayout.LayoutParams cl = Ui.lp(-1, -2);
            cl.topMargin = dp(12);
            col.addView(c, cl);
            c.addView(Ui.texto(this, "Dia " + (m.dia + 1) + " de " + m.calendario.size() + " · " + dia.rotulo, 13, 0xCCFFFFFF, true));
            if (p != null) {
                c.addView(Ui.texto(this, p.comp.nome + " · " + p.fase + (p.mataMata ? " (mata-mata)" : ""), 13, 0xAAFFFFFF, false));
                LinearLayout conf = Ui.linha(this);
                conf.setGravity(Gravity.CENTER);
                conf.setPadding(0, dp(14), 0, dp(10));
                conf.addView(timeBox(m.clube(p.casa)), Ui.lp(0, -2, 1f));
                TextView x = Ui.texto(this, "x", 22, 0xAAFFFFFF, true);
                x.setPadding(dp(8), 0, dp(8), 0);
                conf.addView(x);
                conf.addView(timeBox(m.clube(p.fora)), Ui.lp(0, -2, 1f));
                c.addView(conf);
                TextView local = Ui.texto(this, (p.casa == m.userId ? "🏟️ Em casa · " + eu.capacidade() + " lugares" : "✈️ Fora de casa"), 12, 0xAAFFFFFF, false);
                local.setGravity(Gravity.CENTER);
                c.addView(local);
                Button jogar = Ui.botaoGradiente(this, "⚽  JOGAR PARTIDA");
                jogar.setTextSize(18);
                jogar.setOnClickListener(v -> {
                    jogoAtual = p;
                    int adv = p.casa == m.userId ? p.fora : p.casa;
                    startActivityForResult(new Intent(this, PartidaActivity.class).putExtra("adv", adv).putExtra("mataMata", p.mataMata), REQ_JOGO);
                });
                LinearLayout.LayoutParams jl = Ui.lp(-1, dp(60));
                jl.topMargin = dp(12);
                c.addView(jogar, jl);
                LinearLayout l2 = Ui.linha(this);
                Button esc = Ui.botao(this, "👕 Escalação", false);
                esc.setOnClickListener(v -> {
                    aba = "elenco";
                    mostrar();
                });
                l2.addView(esc, Ui.lp(0, dp(48), 1f));
                Button sim = Ui.botao(this, "⏩ Simular", false);
                sim.setOnClickListener(v -> {
                    Resultado r = m.simular(p);
                    concluir(p, r, null);
                });
                LinearLayout.LayoutParams sl = Ui.lp(0, dp(48), 1f);
                sl.leftMargin = dp(8);
                l2.addView(sim, sl);
                LinearLayout.LayoutParams l2p = Ui.lp(-1, -2);
                l2p.topMargin = dp(8);
                c.addView(l2, l2p);
            } else {
                TextView t = Ui.texto(this, "Seu clube não joga hoje. Os outros jogos serão simulados.", 15, 0xFFFFFFFF, false);
                t.setPadding(0, dp(10), 0, dp(10));
                c.addView(t);
                Button av = Ui.botaoGradiente(this, "Avançar o dia ⏩");
                av.setOnClickListener(v -> concluir(null, null, null));
                c.addView(av, Ui.lp(-1, dp(54)));
            }
        }

        // próximos jogos
        LinearLayout ag = cartao(col);
        titulo(ag, "📅 Próximos dias");
        int n = 0;
        for (int i = m.dia + 1; i < m.calendario.size() && n < 5; i++, n++) {
            Mundo.Dia d = m.calendario.get(i);
            TextView t = Ui.texto(this, "Dia " + (i + 1) + " · " + d.rotulo, 13, k.sub, false);
            t.setPadding(0, dp(6), 0, 0);
            ag.addView(t);
        }
        if (n == 0) ag.addView(Ui.texto(this, "Fim da temporada chegando!", 13, k.sub, false));

        // finanças rápidas
        LinearLayout fi = cartao(col);
        titulo(fi, "💰 Finanças da semana");
        long patro = (eu.divisao == 1 ? 650_000 : eu.divisao == 2 ? 260_000 : 110_000) + eu.reputacao * 4000L;
        fi.addView(linhaValor("Patrocínio", "+" + Modelo.reais(patro), 0xFF4ADE80));
        fi.addView(linhaValor("Salários", "-" + Modelo.reais(eu.folhaSemanal()), 0xFFEF4444));
        fi.addView(linhaValor("Manutenção (estádio, CT, base)", "-" + Modelo.reais((eu.estadio + eu.ct + eu.base) * 12_000L), 0xFFEF4444));
        if (eu.dinheiro < 0)
            fi.addView(Ui.texto(this, "⚠️ Caixa negativo! Venda jogadores ou reduza a folha.", 13, 0xFFEF4444, true));

        // notícias
        LinearLayout no = cartao(col);
        titulo(no, "📰 Notícias");
        for (int i = 0; i < Math.min(12, m.noticias.size()); i++) {
            TextView t = Ui.texto(this, m.noticias.get(i), 13, k.sub, false);
            t.setPadding(0, dp(6), 0, 0);
            no.addView(t);
        }
    }

    private View linhaValor(String r, String v, int cor) {
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(6), 0, 0);
        l.addView(Ui.texto(this, r, 14, k.texto, false), Ui.lp(0, -2, 1f));
        l.addView(Ui.texto(this, v, 14, cor, true));
        return l;
    }

    private View timeBox(Clube c) {
        LinearLayout b = Ui.coluna(this);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.addView(new Escudo(this).de(c), Ui.lp(dp(58), dp(66)));
        TextView n = Ui.texto(this, c.nome, 14, 0xFFFFFFFF, true);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        b.addView(n);
        b.addView(Ui.texto(this, "Força " + c.forca() + " · " + Mundo.nomeDivisao(c), 11, 0xAAFFFFFF, false));
        return b;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req != REQ_JOGO || res != RESULT_OK || d == null || jogoAtual == null) return;
        Partida p = jogoAtual;
        jogoAtual = null;
        int meus = d.getIntExtra("meus", 0), deles = d.getIntExtra("deles", 0);
        boolean casa = p.casa == m.userId;
        Resultado r = new Resultado();
        r.casa = p.casa;
        r.fora = p.fora;
        r.fase = p.fase;
        r.gc = casa ? meus : deles;
        r.gf = casa ? deles : meus;
        ArrayList<String> au = d.getStringArrayListExtra("autores");
        if (au != null) for (String s : au) {
            boolean meu = s.startsWith("0");
            String nome = s.substring(1);
            r.autores.add(((meu == casa) ? "C" : "F") + nome);
        }
        String extra = "Chances criadas: " + d.getIntExtra("chances", 0) + " · Finalizações: " + d.getIntExtra("chutes", 0);
        int pm = d.getIntExtra("penMeu", -1), pd = d.getIntExtra("penDeles", -1);
        if (p.mataMata && r.gc == r.gf) {
            if (pm >= 0 && pm != pd) {
                r.pc = casa ? pm : pd;
                r.pf = casa ? pd : pm;
            } else m.penaltis(r, m.clube(r.casa).forca(), m.clube(r.fora).forca());
        }
        concluir(p, r, extra);
    }

    /** registra o jogo do usuário (se houver), simula o resto do dia e mostra o resumo */
    private void concluir(Partida p, Resultado meu, String extra) {
        Clube eu = m.eu();
        long antes = eu.dinheiro;
        List<Resultado> rs = m.concluirDia(meu);
        StringBuilder s = new StringBuilder();
        if (meu != null) {
            Clube a = m.clube(meu.casa), b = m.clube(meu.fora);
            int v = meu.vencedor();
            s.append(v == m.userId ? "✅ VITÓRIA!" : v == 0 ? "🤝 Empate" : "❌ Derrota").append("\n");
            s.append(a.nome).append(" ").append(meu.gc).append(" x ").append(meu.gf).append(" ").append(b.nome);
            if (meu.pc >= 0) s.append("\nPênaltis: ").append(meu.pc).append(" x ").append(meu.pf);
            if (extra != null) s.append("\n").append(extra);
            List<String> gols = new ArrayList<>();
            for (String x : meu.autores) if (x.length() > 1) gols.add("⚽ " + x.substring(1) + " (" + (x.charAt(0) == 'C' ? a.sigla : b.sigla) + ")");
            if (!gols.isEmpty()) s.append("\n\n").append(TextUtils.join("\n", gols));
            m.noticia((v == m.userId ? "Vitória: " : v == 0 ? "Empate: " : "Derrota: ") + a.sigla + " " + meu.gc + " x " + meu.gf + " " + b.sigla + " (" + p.comp.nome + ")");
            if (p.fase.equals("Final") && v == m.userId) {
                s.append("\n\n🏆 CAMPEÃO DO ").append(p.comp.nome.toUpperCase()).append("!");
                m.noticia("🏆 " + eu.nome + " é campeão: " + p.comp.nome + "!");
            } else if (p.mataMata && v != m.userId) s.append("\n\nEliminado do ").append(p.comp.nome).append(".");
        } else s.append("Jogos do dia simulados.");
        // outros campeões definidos hoje
        for (Comp c : m.comps) {
            for (Resultado r : rs) if (r.fase.equals("Final") && c.resultados.contains(r) && r.vencedor() != m.userId && r.vencedor() != 0)
                m.noticia("🏆 " + m.clube(r.vencedor()).nome + " conquista: " + c.nome);
        }
        long dif = eu.dinheiro - antes;
        s.append("\n\n💰 Caixa da semana: ").append(dif >= 0 ? "+" : "").append(Modelo.reais(dif));
        Sessao.salvar(this);
        new AlertDialog.Builder(this).setTitle(meu != null ? "Resultado" : "Dia concluído").setMessage(s.toString()).setPositiveButton("Continuar", null).show();
        mostrar();
    }

    // ================================================================== ELENCO

    private void abaElenco() {
        LinearLayout col = pagina();
        Clube eu = m.eu();
        eu.validar();
        LinearLayout c = cartao(col);
        LinearLayout t = Ui.linha(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.addView(Ui.texto(this, "Escalação · força " + eu.forca(), 16, k.texto, true), Ui.lp(0, -2, 1f));
        TextView auto = Ui.texto(this, "⚡ Auto", 14, k.destaque, true);
        auto.setPadding(dp(10), dp(6), dp(4), dp(6));
        auto.setOnClickListener(v -> {
            eu.autoEscalar();
            Sessao.salvar(this);
            mostrar();
        });
        t.addView(auto);
        c.addView(t);
        c.addView(new Ui.Chips(this, Modelo.FORMACOES, Modelo.FORMACOES, eu.posicoes == null ? eu.formacao : "", id -> {
            eu.formacao = id;
            eu.posicoes = null;
            eu.autoEscalar();
            Sessao.salvar(this);
            mostrar();
        }).view);
        float ment = eu.mentalidade();
        String mt = ment > 0.53f ? "⚔️ Ofensiva: cria mais chances, mas deixa espaços atrás" : ment < 0.43f ? "🛡️ Defensiva: sofre menos chances, mas ataca pouco" : "⚖️ Equilibrada";
        LinearLayout ml = Ui.linha(this);
        ml.setGravity(Gravity.CENTER_VERTICAL);
        ml.setPadding(0, dp(4), 0, dp(8));
        ml.addView(Ui.texto(this, "Mentalidade: " + mt, 13, k.texto, false), Ui.lp(0, -2, 1f));
        if (eu.posicoes != null) {
            TextView rs = Ui.texto(this, "↺ Restaurar", 13, k.destaque, true);
            rs.setPadding(dp(8), dp(6), 0, dp(6));
            rs.setOnClickListener(v -> {
                eu.posicoes = null;
                Sessao.salvar(this);
                mostrar();
            });
            ml.addView(rs);
        }
        c.addView(ml);
        c.addView(campinho(eu), Ui.lp(-1, dp(400)));
        c.addView(Ui.texto(this, "Arraste os jogadores para montar sua tática livremente · toque para trocar quem joga.", 13, k.sub, false));

        LinearLayout l = cartao(col);
        titulo(l, "Elenco (" + eu.elenco.size() + ") · folha " + Modelo.reais(eu.folhaSemanal()) + "/sem");
        java.util.Set<Integer> tit = new java.util.HashSet<>();
        for (int id : eu.titular) tit.add(id);
        for (Jogador j : Modelo.ordenarElenco(eu.elenco)) l.addView(linhaJogador(j, tit.contains(j.id), v -> detalheJogador(j, true)));
    }

    private View campinho(Clube eu) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2E8B3A, 0xFF267A31});
        g.setCornerRadius(dp(16));
        f.setBackground(g);
        View linhas = new View(this) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(android.graphics.Canvas c) {
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.5f));
                p.setColor(0x66FFFFFF);
                float w = getWidth(), h = getHeight();
                c.drawRect(dp(8), dp(8), w - dp(8), h - dp(8), p);
                c.drawLine(dp(8), h / 2, w - dp(8), h / 2, p);
                c.drawCircle(w / 2, h / 2, w * .13f, p);
                c.drawRect(w * .25f, dp(8), w * .75f, h * .15f, p);
                c.drawRect(w * .25f, h * .85f, w * .75f, h - dp(8), p);
            }
        };
        f.addView(linhas, new FrameLayout.LayoutParams(-1, -1));
        float[][] v = eu.vagas();
        f.post(() -> {
            int w = f.getWidth(), h = f.getHeight();
            for (int i = 0; i < 11; i++) {
                final int vaga = i;
                Jogador j = eu.jogador(eu.titular[i]);
                int pos = (int) v[i][2];
                LinearLayout b = Ui.coluna(this);
                b.setGravity(Gravity.CENTER_HORIZONTAL);
                TextView ov = Ui.texto(this, String.valueOf(j.ovrEm(pos)), 13, 0xFFFFFFFF, true);
                ov.setGravity(Gravity.CENTER);
                GradientDrawable bo = new GradientDrawable();
                bo.setShape(GradientDrawable.OVAL);
                bo.setColor(j.pos == pos ? eu.cor1 : 0xFFB45309);
                bo.setStroke(dp(2), eu.cor2);
                ov.setBackground(bo);
                if (!dark(eu.cor1)) ov.setTextColor(0xFF111111);
                b.addView(ov, Ui.lp(dp(34), dp(34)));
                TextView nm = Ui.texto(this, curto(j.nome), 10, 0xFFFFFFFF, true);
                nm.setGravity(Gravity.CENTER);
                nm.setShadowLayer(3, 0, 1, 0xFF000000);
                nm.setMaxLines(1);
                b.addView(nm, Ui.lp(dp(72), -2));
                TextView ps = Ui.texto(this, Modelo.POS[pos], 9, 0xDDFFFFFF, false);
                b.addView(ps);
                arrastavel(b, eu, vaga, pos, w, h);
                float px = v[i][1] * w, py = h - (v[i][0] * (h - dp(60)) + dp(30));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(72), -2);
                lp.leftMargin = (int) (px - dp(36));
                lp.topMargin = (int) (py - dp(24));
                f.addView(b, lp);
            }
        });
        return f;
    }

    /** toque = trocar jogador; arrastar = mudar a posição livremente */
    private void arrastavel(View b, Clube eu, int vaga, int pos, int w, int h) {
        final float[] ini = new float[4];
        final boolean[] moveu = {false};
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    ini[0] = e.getRawX();
                    ini[1] = e.getRawY();
                    moveu[0] = false;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - ini[0], dy = e.getRawY() - ini[1];
                    if (Math.hypot(dx, dy) > dp(8)) moveu[0] = true;
                    if (moveu[0]) {
                        v.setTranslationX(dx);
                        v.setTranslationY(dy);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1).scaleY(1).setDuration(120).start();
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (!moveu[0]) {
                        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) trocarVaga(eu, vaga, pos);
                        return true;
                    }
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                    float cxp = lp.leftMargin + dp(36) + v.getTranslationX(), cyp = lp.topMargin + dp(24) + v.getTranslationY();
                    float ny = Math.max(0.04f, Math.min(0.96f, cxp / w));
                    float nx = Math.max(0.02f, Math.min(0.9f, (h - cyp - dp(30)) / (h - dp(60))));
                    if (vaga == 0) nx = Math.min(nx, 0.1f);
                    else nx = Math.max(nx, 0.12f);
                    float[][] atual = eu.vagas();
                    if (eu.posicoes == null) {
                        eu.posicoes = new float[11][2];
                        for (int i = 0; i < 11; i++) {
                            eu.posicoes[i][0] = atual[i][0];
                            eu.posicoes[i][1] = atual[i][1];
                        }
                    }
                    eu.posicoes[vaga][0] = nx;
                    eu.posicoes[vaga][1] = ny;
                    Sessao.salvar(this);
                    mostrar();
                    return true;
            }
            return false;
        });
    }

    static boolean dark(int c) {
        return (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000 < 170;
    }

    static String curto(String n) {
        String[] p = n.split(" ");
        return p.length > 1 ? p[p.length - 1] : n;
    }

    private void trocarVaga(Clube eu, int vaga, int pos) {
        List<Jogador> l = new ArrayList<>(eu.elenco);
        l.sort((a, b) -> b.ovrEm(pos) - a.ovrEm(pos));
        String[] nomes = new String[l.size()];
        java.util.Set<Integer> tit = new java.util.HashSet<>();
        for (int id : eu.titular) tit.add(id);
        for (int i = 0; i < l.size(); i++) {
            Jogador j = l.get(i);
            nomes[i] = j.ovrEm(pos) + "  ·  " + j.nome + "  (" + Modelo.POS[j.pos] + ")" + (tit.contains(j.id) ? "  ✓ titular" : "");
        }
        new AlertDialog.Builder(this).setTitle("Quem joga de " + Modelo.POS_NOME[pos] + "?").setItems(nomes, (d, w) -> {
            Jogador novo = l.get(w);
            int atual = eu.titular[vaga];
            for (int i = 0; i < 11; i++) if (eu.titular[i] == novo.id) eu.titular[i] = atual; // troca de vaga
            eu.titular[vaga] = novo.id;
            Sessao.salvar(this);
            mostrar();
        }).show();
    }

    interface Clique {
        void ok(View v);
    }

    private View linhaJogador(Jogador j, boolean titular, View.OnClickListener cl) {
        LinearLayout l = Ui.linha(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(8), 0, dp(8));
        TextView pos = Ui.texto(this, Modelo.POS[j.pos], 11, 0xFFFFFFFF, true);
        pos.setGravity(Gravity.CENTER);
        pos.setBackground(Ui.fundo(Modelo.POS_COR[j.pos], dp(8)));
        l.addView(pos, Ui.lp(dp(40), dp(24)));
        LinearLayout t = Ui.coluna(this);
        t.setPadding(dp(10), 0, dp(6), 0);
        TextView n = Ui.texto(this, (titular ? "★ " : "") + j.nome, 15, k.texto, titular);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(n);
        t.addView(Ui.texto(this, j.idade + " anos · " + Modelo.reais(j.valor()) + (j.gols > 0 ? " · ⚽ " + j.gols : ""), 12, k.sub, false));
        l.addView(t, Ui.lp(0, -2, 1f));
        TextView ov = Ui.texto(this, String.valueOf(j.ovr()), 18, corNota(j.ovr()), true);
        ov.setGravity(Gravity.END);
        l.addView(ov, Ui.lp(dp(40), -2));
        l.setOnClickListener(cl);
        return l;
    }

    static int corNota(int o) {
        return o >= 80 ? 0xFF22D3EE : o >= 70 ? 0xFF4ADE80 : o >= 60 ? 0xFFFACC15 : o >= 50 ? 0xFFFB923C : 0xFFEF4444;
    }

    private void detalheJogador(Jogador j, boolean meu) {
        LinearLayout v = Ui.coluna(this);
        v.setPadding(dp(22), dp(14), dp(22), dp(6));
        v.addView(Ui.texto(this, Modelo.POS_NOME[j.pos] + " · " + j.idade + " anos · camisa " + j.numero, 13, k.sub, false));
        LinearLayout big = Ui.linha(this);
        big.setGravity(Gravity.CENTER_VERTICAL);
        big.addView(Ui.texto(this, String.valueOf(j.ovr()), 40, corNota(j.ovr()), true));
        String pot = meu ? "Potencial " + j.pot : "Potencial " + Math.max(j.ovr(), j.pot - 4) + "–" + Math.min(99, j.pot + 4);
        TextView pt = Ui.texto(this, "  " + pot, 15, k.sub, false);
        big.addView(pt);
        v.addView(big);
        for (int i = 0; i < 6; i++) {
            if (j.pos != 0 && i == 5) continue;
            LinearLayout r = Ui.linha(this);
            r.setPadding(0, dp(6), 0, 0);
            r.addView(Ui.texto(this, Modelo.ATR[i], 13, k.texto, false), Ui.lp(dp(96), -2));
            FrameLayout bar = new FrameLayout(this);
            bar.setBackground(Ui.fundo(k.linha, dp(5)));
            View f = new View(this);
            f.setBackground(Ui.fundo(corNota(j.a[i]), dp(5)));
            int finalI = i;
            bar.addView(f, new FrameLayout.LayoutParams(0, -1));
            bar.post(() -> {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) f.getLayoutParams();
                lp.width = bar.getWidth() * j.a[finalI] / 100;
                f.setLayoutParams(lp);
            });
            r.addView(bar, Ui.lp(0, dp(10), 1f));
            r.addView(Ui.texto(this, "  " + j.a[i], 13, k.texto, true), Ui.lp(dp(34), -2));
            v.addView(r);
        }
        TextView est = Ui.texto(this, "Temporada: " + j.jogos + " jogos · " + j.gols + " gols\nCarreira: " + j.jogosCarreira + " jogos · " + j.golsCarreira + " gols\nValor " + Modelo.reais(j.valor()) + " · Salário " + Modelo.reais(j.salario()) + "/semana", 13, k.sub, false);
        est.setPadding(0, dp(12), 0, 0);
        v.addView(est);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(j.nome).setView(v).setNegativeButton("Fechar", null);
        if (meu) b.setPositiveButton("Vender", (d, w) -> vender(j));
        b.show();
    }

    private void vender(Jogador j) {
        long prop = m.proposta(j);
        new AlertDialog.Builder(this).setTitle("Proposta por " + j.nome).setMessage("Um clube oferece " + Modelo.reais(prop) + " (valor de mercado " + Modelo.reais(j.valor()) + "). Aceitar?")
                .setPositiveButton("Vender", (d, w) -> {
                    String erro = m.vender(j, prop);
                    if (erro != null) Toast.makeText(this, erro, Toast.LENGTH_LONG).show();
                    Sessao.salvar(this);
                    mostrar();
                }).setNeutralButton("Pedir mais", (d, w) -> {
                    if (m.rnd.nextInt(100) < 45) {
                        long nova = Math.round(prop * (1.08 + m.rnd.nextDouble() * 0.12) / 1000) * 1000;
                        new AlertDialog.Builder(this).setTitle("Contraproposta aceita?").setMessage("Eles sobem para " + Modelo.reais(nova) + ".")
                                .setPositiveButton("Vender", (a, x) -> {
                                    m.vender(j, nova);
                                    Sessao.salvar(this);
                                    mostrar();
                                }).setNegativeButton("Recusar", null).show();
                    } else Toast.makeText(this, "O clube desistiu da negociação.", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Recusar", null).show();
    }

    // ================================================================== MERCADO

    private void abaMercado() {
        LinearLayout col = pagina();
        Clube eu = m.eu();
        LinearLayout c = cartao(col);
        titulo(c, "🔁 Mercado da bola");
        c.addView(Ui.texto(this, "A lista muda a cada 4 dias. Elenco: " + eu.elenco.size() + "/32 · Caixa " + Modelo.reais(eu.dinheiro), 13, k.sub, false));
        String[] ids = {"todos", "0", "1", "2", "3", "4", "5"};
        String[] ns = {"Todos", "Goleiros", "Zagueiros", "Laterais", "Volantes", "Meias", "Atacantes"};
        c.addView(new Ui.Chips(this, ids, ns, filtroPos, id -> {
            filtroPos = id;
            mostrar();
        }).view);
        LinearLayout l = cartao(col);
        int n = 0;
        for (Mundo.Oferta o : new ArrayList<>(m.mercado)) {
            if (!filtroPos.equals("todos") && o.j.pos != Integer.parseInt(filtroPos)) continue;
            Clube orig = m.clube(o.clube);
            LinearLayout r = Ui.linha(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(8), 0, dp(8));
            TextView pos = Ui.texto(this, Modelo.POS[o.j.pos], 11, 0xFFFFFFFF, true);
            pos.setGravity(Gravity.CENTER);
            pos.setBackground(Ui.fundo(Modelo.POS_COR[o.j.pos], dp(8)));
            r.addView(pos, Ui.lp(dp(40), dp(24)));
            LinearLayout t = Ui.coluna(this);
            t.setPadding(dp(10), 0, dp(6), 0);
            TextView nm = Ui.texto(this, o.j.nome, 15, k.texto, true);
            nm.setMaxLines(1);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(nm);
            t.addView(Ui.texto(this, o.j.idade + " anos · " + (orig != null ? orig.nome : "Sem clube"), 12, k.sub, false));
            r.addView(t, Ui.lp(0, -2, 1f));
            LinearLayout pr = Ui.coluna(this);
            pr.setGravity(Gravity.END);
            pr.addView(Ui.texto(this, String.valueOf(o.j.ovr()), 18, corNota(o.j.ovr()), true));
            pr.addView(Ui.texto(this, Modelo.reais(o.preco), 12, o.preco <= eu.dinheiro ? k.destaque : 0xFFEF4444, true));
            r.addView(pr);
            r.setOnClickListener(v -> comprar(o));
            l.addView(r);
            n++;
        }
        if (n == 0) l.addView(Ui.texto(this, "Nenhum jogador dessa posição no mercado agora.", 14, k.sub, false));
    }

    private void comprar(Mundo.Oferta o) {
        Jogador j = o.j;
        String pot = "Potencial estimado " + Math.max(j.ovr(), j.pot - 4) + "–" + Math.min(99, j.pot + 4);
        new AlertDialog.Builder(this).setTitle(j.nome + " · " + j.ovr())
                .setMessage(Modelo.POS_NOME[j.pos] + ", " + j.idade + " anos\n" + pot + "\n\nVel " + j.a[0] + " · Fin " + j.a[1] + " · Pas " + j.a[2]
                        + " · Dri " + j.a[3] + " · Def " + j.a[4] + (j.pos == 0 ? " · Gol " + j.a[5] : "") + "\n\nPreço: " + Modelo.reais(o.preco)
                        + "\nSalário: " + Modelo.reais(j.salario()) + "/semana\nSeu caixa: " + Modelo.reais(m.eu().dinheiro))
                .setPositiveButton("Contratar", (d, w) -> {
                    String erro = m.comprar(o);
                    if (erro != null) Toast.makeText(this, erro, Toast.LENGTH_LONG).show();
                    else Toast.makeText(this, j.nome + " é do " + m.eu().nome + "! ✍️", Toast.LENGTH_SHORT).show();
                    Sessao.salvar(this);
                    mostrar();
                }).setNegativeButton("Voltar", null).show();
    }

    // ================================================================== CLUBE

    private void abaClube() {
        LinearLayout col = pagina();
        Clube eu = m.eu();
        LinearLayout c = cartao(col);
        LinearLayout t = Ui.linha(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.addView(new Escudo(this).de(eu), Ui.lp(dp(70), dp(80)));
        LinearLayout tx = Ui.coluna(this);
        tx.setPadding(dp(14), 0, 0, 0);
        tx.addView(Ui.texto(this, eu.nome, 20, k.texto, true));
        tx.addView(Ui.texto(this, "Presidente: você · Reputação " + eu.reputacao + "/100", 13, k.sub, false));
        tx.addView(Ui.texto(this, "Caixa " + Modelo.reais(eu.dinheiro), 15, k.destaque, true));
        t.addView(tx, Ui.lp(0, -2, 1f));
        c.addView(t);

        estrutura(col, "🏟️ Estádio", "Nível " + eu.estadio + " · " + eu.capacidade() + " lugares · ocupação média " + m.ocupacao(eu) + "%",
                "Mais lugares = mais renda nos jogos em casa.", eu.estadio, Modelo.custoEstadio(eu.estadio), () -> eu.estadio++);
        estrutura(col, "🏋️ Centro de Treinamento", "Nível " + eu.ct, "Quanto maior, mais rápido os jogadores evoluem até o potencial.",
                eu.ct, Modelo.custoCt(eu.ct), () -> eu.ct++);
        estrutura(col, "🌱 Categoria de Base", "Nível " + eu.base, "A cada temporada a base revela 2 jovens; quanto maior o nível, melhores eles são.",
                eu.base, Modelo.custoBase(eu.base), () -> eu.base++);

        LinearLayout ing = cartao(col);
        titulo(ing, "🎟️ Preço do ingresso");
        TextView pv = Ui.texto(this, "", 15, k.texto, false);
        Runnable pintar = () -> pv.setText("R$ " + eu.precoIngresso + " · ocupação estimada " + m.ocupacao(eu) + "% · renda ~" +
                Modelo.reais((long) eu.capacidade() * m.ocupacao(eu) / 100 * eu.precoIngresso) + " por jogo");
        pintar.run();
        ing.addView(pv);
        LinearLayout bt = Ui.linha(this);
        bt.setPadding(0, dp(8), 0, 0);
        for (int dv : new int[]{-10, -5, 5, 10}) {
            Button b = Ui.botao(this, (dv > 0 ? "+" : "") + dv, false);
            b.setOnClickListener(v -> {
                eu.precoIngresso = Math.max(5, Math.min(300, eu.precoIngresso + dv));
                pintar.run();
                Sessao.salvar(this);
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, dp(44), 1f);
            lp.rightMargin = dp(6);
            bt.addView(b, lp);
        }
        ing.addView(bt);

        LinearLayout tt = cartao(col);
        titulo(tt, "🏆 Sala de troféus (" + m.titulos.size() + ")");
        if (m.titulos.isEmpty()) tt.addView(Ui.texto(this, "Ainda sem títulos. O primeiro está chegando!", 13, k.sub, false));
        for (String s : m.titulos) tt.addView(Ui.texto(this, "🏆 " + s, 14, k.texto, false));
    }

    private void estrutura(LinearLayout col, String nome, String info, String dica, int nivel, long custo, Runnable subir) {
        LinearLayout c = cartao(col);
        titulo(c, nome);
        c.addView(Ui.texto(this, info, 14, k.texto, false));
        LinearLayout barras = Ui.linha(this);
        barras.setPadding(0, dp(8), 0, dp(6));
        for (int i = 1; i <= 10; i++) {
            View b = new View(this);
            b.setBackground(Ui.fundo(i <= nivel ? k.destaque : k.linha, dp(3)));
            LinearLayout.LayoutParams lp = Ui.lp(0, dp(8), 1f);
            lp.rightMargin = dp(3);
            barras.addView(b, lp);
        }
        c.addView(barras);
        c.addView(Ui.texto(this, dica, 12, k.sub, false));
        if (nivel < 10) {
            Button b = Ui.botao(this, "Construir nível " + (nivel + 1) + " · " + Modelo.reais(custo), false);
            b.setOnClickListener(v -> {
                Clube eu = m.eu();
                if (eu.dinheiro < custo) {
                    Toast.makeText(this, "Faltam " + Modelo.reais(custo - eu.dinheiro), Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(this).setTitle(nome).setMessage("Investir " + Modelo.reais(custo) + " para o nível " + (nivel + 1) + "?")
                        .setPositiveButton("Construir", (d, w) -> {
                            eu.dinheiro -= custo;
                            subir.run();
                            m.noticia("🏗️ Obra concluída: " + nome.substring(nome.indexOf(' ') + 1) + " nível " + (nivel + 1));
                            Sessao.salvar(this);
                            mostrar();
                        }).setNegativeButton("Cancelar", null).show();
            });
            LinearLayout.LayoutParams lp = Ui.lp(-1, dp(46));
            lp.topMargin = dp(8);
            c.addView(b, lp);
        } else c.addView(Ui.texto(this, "Nível máximo! 🏅", 13, k.destaque, true));
    }

    // ================================================================== TORNEIOS

    private void abaTorneios() {
        LinearLayout col = pagina();
        Clube eu = m.eu();
        List<String> ids = new ArrayList<>(), ns = new ArrayList<>();
        String minhaLiga = eu.divisao == 1 ? "serieA" : eu.divisao == 2 ? "serieB" : "serieC";
        for (String id : new String[]{"est_" + eu.uf, minhaLiga, "copa", "cont", "mund", "serieA", "serieB", "serieC"}) {
            Comp c = m.comp(id);
            if (c == null || ids.contains(id)) continue;
            ids.add(id);
            String n = c.nome.replace("Liga Nacional · ", "").replace("Estadual ", "Est. ").replace("Copa das Américas de Clubes", "Copa das Américas").replace("Copa dos Campeões Mundial", "Campeões Mundial");
            ns.add((c.times.contains(m.userId) ? "⭐ " : "") + n);
        }
        for (String uf : Mundo.UFS) if (!uf.equals(eu.uf)) {
            ids.add("est_" + uf);
            ns.add("Est. " + uf);
        }
        if (compSel == null || m.comp(compSel) == null) compSel = minhaLiga;
        col.addView(new Ui.Chips(this, ids.toArray(new String[0]), ns.toArray(new String[0]), compSel, id -> {
            compSel = id;
            mostrar();
        }).view);
        Comp c = m.comp(compSel);
        LinearLayout card = cartao(col);
        titulo(card, c.nome);
        if (c.campeao != 0) card.addView(Ui.texto(this, "🏆 Campeão: " + m.clube(c.campeao).nome, 14, 0xFFFACC15, true));
        if (c.tipo.equals("liga")) tabela(card, c);
        else chave(card, c);

        LinearLayout art = cartao(col);
        titulo(art, "⚽ Artilharia (temporada)");
        List<Jogador[]> top = m.artilheiros(c, 8);
        if (top.isEmpty()) art.addView(Ui.texto(this, "Ninguém marcou ainda.", 13, k.sub, false));
        for (Jogador[] x : top) {
            Clube cl = m.clubeDoJogador(x[0].id);
            art.addView(linhaValor(x[0].nome + " (" + (cl != null ? cl.sigla : "") + ")", x[0].gols + " gols", k.texto));
        }

        LinearLayout ult = cartao(col);
        titulo(ult, "Últimos resultados");
        int n = 0;
        for (int i = c.resultados.size() - 1; i >= 0 && n < 12; i--, n++) {
            Resultado r = c.resultados.get(i);
            String s = m.clube(r.casa).sigla + " " + r.gc + " x " + r.gf + " " + m.clube(r.fora).sigla + (r.pc >= 0 ? " (pên. " + r.pc + "x" + r.pf + ")" : "") + " · " + r.fase;
            boolean meu = r.casa == m.userId || r.fora == m.userId;
            TextView t = Ui.texto(this, s, 13, meu ? k.destaque : k.sub, meu);
            t.setPadding(0, dp(5), 0, 0);
            ult.addView(t);
        }
        if (n == 0) ult.addView(Ui.texto(this, "A competição ainda não começou.", 13, k.sub, false));
    }

    private void tabela(LinearLayout card, Comp c) {
        LinearLayout h = Ui.linha(this);
        h.setPadding(0, dp(8), 0, dp(4));
        h.addView(Ui.texto(this, "#  Clube", 12, k.sub, true), Ui.lp(0, -2, 1f));
        for (String s : new String[]{"J", "V", "E", "D", "SG", "P"}) {
            TextView t = Ui.texto(this, s, 12, k.sub, true);
            t.setGravity(Gravity.END);
            h.addView(t, Ui.lp(dp(s.equals("SG") ? 34 : 26), -2));
        }
        card.addView(h);
        List<Integer> cl = c.classificacao();
        boolean liga = c.id.startsWith("serie");
        for (int i = 0; i < cl.size(); i++) {
            int id = cl.get(i);
            int[] t = c.tabela.get(id);
            Clube x = m.clube(id);
            LinearLayout r = Ui.linha(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(4), dp(6), 0, dp(6));
            if (id == m.userId) r.setBackground(Ui.fundo(k.chipSel, dp(8)));
            int cor = k.texto;
            if (liga) {
                if (i < 3 && !c.id.equals("serieA")) cor = 0xFF4ADE80;
                if (i < 4 && c.id.equals("serieA")) cor = 0xFF22D3EE;
                if (i >= cl.size() - 3 && !c.id.equals("serieC")) cor = 0xFFEF4444;
            } else if (i < 2) cor = 0xFF4ADE80;
            TextView pos = Ui.texto(this, (i + 1) + "", 13, cor, true);
            r.addView(pos, Ui.lp(dp(24), -2));
            Escudo e = new Escudo(this).de(x);
            r.addView(e, Ui.lp(dp(18), dp(20)));
            TextView n = Ui.texto(this, " " + x.nome, 13, k.texto, id == m.userId);
            n.setMaxLines(1);
            n.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(n, Ui.lp(0, -2, 1f));
            int[] vals = {t[0], t[1], t[2], t[3], t[4] - t[5], c.pontos(id)};
            for (int k2 = 0; k2 < 6; k2++) {
                TextView v = Ui.texto(this, String.valueOf(vals[k2]), 13, k2 == 5 ? k.texto : k.sub, k2 == 5);
                v.setGravity(Gravity.END);
                r.addView(v, Ui.lp(dp(k2 == 4 ? 34 : 26), -2));
            }
            card.addView(r);
        }
        String leg = liga ? (c.id.equals("serieA") ? "Azul: vaga na Copa das Américas · Vermelho: rebaixamento" : c.id.equals("serieC") ? "Verde: acesso à Série B" : "Verde: acesso à Série A · Vermelho: rebaixamento")
                : "Os 2 primeiros fazem a final do estadual";
        TextView l = Ui.texto(this, leg, 11, k.sub, false);
        l.setPadding(0, dp(8), 0, 0);
        card.addView(l);
    }

    private void chave(LinearLayout card, Comp c) {
        card.addView(Ui.texto(this, c.campeao != 0 ? "Competição encerrada." : c.vivos.size() + " clubes ainda vivos" + (c.vivos.contains(m.userId) ? " (incluindo você!)" : c.times.contains(m.userId) ? " · você foi eliminado" : ""), 13, k.sub, false));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(10), 0, dp(4));
        hs.addView(l);
        List<Integer> vivos = new ArrayList<>(c.vivos);
        vivos.sort((a, b) -> m.clube(b).forca() - m.clube(a).forca());
        for (int id : vivos) {
            Clube x = m.clube(id);
            LinearLayout b = Ui.coluna(this);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            b.setPadding(dp(6), 0, dp(6), 0);
            b.addView(new Escudo(this).de(x), Ui.lp(dp(34), dp(40)));
            b.addView(Ui.texto(this, x.sigla, 11, id == m.userId ? k.destaque : k.sub, true));
            l.addView(b);
        }
        card.addView(hs);
        if (!c.times.contains(m.userId)) {
            String como = c.id.equals("cont") ? "Termine entre os 4 primeiros da Série A para disputar." : c.id.equals("mund") ? "Seja campeão da Série A ou da Copa das Américas para disputar." : "";
            if (!como.isEmpty()) card.addView(Ui.texto(this, "🔒 " + como, 13, 0xFFFACC15, false));
        }
    }

    // ================================================================== ajustes

    private void ajustes() {
        String[] op = {"🎮 Dificuldade: " + new String[]{"Fácil", "Normal", "Difícil"}[m.dificuldade], "⏱️ Duração da partida: " + m.minutosPartida + " min",
                "💾 Salvar agora", "🏠 Menu principal"};
        new AlertDialog.Builder(this).setItems(op, (d, w) -> {
            if (w == 0) new AlertDialog.Builder(this).setItems(new String[]{"Fácil", "Normal", "Difícil"}, (a, x) -> {
                m.dificuldade = x;
                Sessao.salvar(this);
            }).show();
            else if (w == 1) {
                int[] mins = {3, 4, 6, 8, 10};
                String[] ns = {"3 minutos", "4 minutos", "6 minutos", "8 minutos", "10 minutos"};
                new AlertDialog.Builder(this).setItems(ns, (a, x) -> {
                    m.minutosPartida = mins[x];
                    Sessao.salvar(this);
                }).show();
            } else if (w == 2) {
                Sessao.salvar(this);
                Toast.makeText(this, "Carreira salva ✅", Toast.LENGTH_SHORT).show();
            } else finish();
        }).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Sessao.salvar(this);
    }
}
