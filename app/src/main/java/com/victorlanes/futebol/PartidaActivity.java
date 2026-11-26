package com.victorlanes.futebol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.victorlanes.futebol.Modelo.Clube;
import com.victorlanes.futebol.Modelo.Jogador;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Partida no estilo New Star Soccer: o tempo corre com a narração e, nos momentos decisivos,
 * o jogo para em 3D para você decidir o lance com o dedo.
 */
public class PartidaActivity extends Activity {

    private static final int MEU = 0, DELES = 1, NARRA = 2;

    private Mundo m;
    private Clube meu, adv;
    private Cena3D cena;
    private TextView placarTv, minutoTv, narracao, banner;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final List<int[]> eventos = new ArrayList<>(); // minuto, tipo
    private final List<String> linhas = new ArrayList<>();
    private float minuto;
    private int proximo;
    private boolean pausado, rodando, intervalo, emLance, acabou, mataMata;
    private int[] placar = new int[2];
    private final ArrayList<String> autores = new ArrayList<>();
    private int chutes, chances;
    private float segundosPorMinuto;
    private float convDeles;
    // pênaltis
    private boolean disputaPenaltis;
    private int penMeu, penDeles, cobrancasMeu, cobrancasDeles;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        m = Sessao.mundo(this);
        if (m == null) {
            finish();
            return;
        }
        meu = m.eu();
        adv = m.clube(getIntent().getIntExtra("adv", 0));
        mataMata = getIntent().getBooleanExtra("mataMata", false);
        if (adv == null) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28)
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        montarTela();
        planejar();
        telaCheia();
        narrar("⚽ Bola rolando! " + meu.nome + " x " + adv.nome);
        rodando = true;
        h.postDelayed(relogio, 600);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ================================================================== tela

    private void montarTela() {
        FrameLayout raiz = new FrameLayout(this);
        cena = new Cena3D(this);
        cena.clubes(meu, adv);
        raiz.addView(cena, new FrameLayout.LayoutParams(-1, -1));

        // placar
        LinearLayout pl = Ui.linha(this);
        pl.setGravity(Gravity.CENTER_VERTICAL);
        pl.setBackground(Ui.fundo(0xE60A130E, dp(18)));
        pl.setPadding(dp(10), dp(6), dp(14), dp(6));
        pl.addView(new Escudo(this).de(meu), Ui.lp(dp(26), dp(30)));
        placarTv = Ui.texto(this, "", 19, 0xFFFFFFFF, true);
        placarTv.setPadding(dp(10), 0, dp(10), 0);
        pl.addView(placarTv);
        pl.addView(new Escudo(this).de(adv), Ui.lp(dp(26), dp(30)));
        minutoTv = Ui.texto(this, "0'", 15, 0xFF4ADE80, true);
        minutoTv.setPadding(dp(14), 0, 0, 0);
        pl.addView(minutoTv);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(10);
        raiz.addView(pl, lp);

        // botões
        LinearLayout bt = Ui.linha(this);
        TextView pular = botao("⏩", v -> pularParaLance());
        TextView pausa = botao("II", v -> pausa());
        bt.addView(pular, Ui.lp(dp(46), dp(46)));
        LinearLayout.LayoutParams pp = Ui.lp(dp(46), dp(46));
        pp.leftMargin = dp(8);
        bt.addView(pausa, pp);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bl.setMargins(0, dp(10), dp(14), 0);
        raiz.addView(bt, bl);

        // narração
        narracao = Ui.texto(this, "", 14, 0xFFFFFFFF, false);
        narracao.setBackground(Ui.fundo(0xCC0A130E, dp(14)));
        narracao.setPadding(dp(14), dp(10), dp(14), dp(10));
        narracao.setLineSpacing(0, 1.15f);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(dp(380), -2, Gravity.BOTTOM | Gravity.START);
        nl.setMargins(dp(14), 0, 0, dp(14));
        raiz.addView(narracao, nl);

        banner = Ui.texto(this, "", 22, 0xFFFFFFFF, true);
        banner.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xEE16A34A, 0xEE65A30D});
        g.setCornerRadius(dp(18));
        banner.setBackground(g);
        banner.setPadding(dp(22), dp(12), dp(22), dp(12));
        banner.setVisibility(View.GONE);
        raiz.addView(banner, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(raiz);
        atualizarPlacar();
    }

    private TextView botao(String s, View.OnClickListener cl) {
        TextView t = Ui.texto(this, s, 17, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.clicavel(0xCC0A130E, dp(23), 0x33FFFFFF));
        t.setOnClickListener(cl);
        return t;
    }

    private void atualizarPlacar() {
        placarTv.setText(meu.sigla + "  " + placar[0] + " x " + placar[1] + "  " + adv.sigla);
        minutoTv.setText(disputaPenaltis ? "Pênaltis " + penMeu + "x" + penDeles : Math.min(90, (int) minuto) + "'");
    }

    private void narrar(String s) {
        linhas.add(0, (disputaPenaltis ? "" : Math.min(90, (int) minuto) + "'  ") + s);
        while (linhas.size() > 3) linhas.remove(linhas.size() - 1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < linhas.size(); i++) b.append(i > 0 ? "\n" : "").append(linhas.get(i));
        narracao.setText(b.toString());
        narracao.setAlpha(1);
    }

    private void mostrarBanner(String s, long ms) {
        banner.setText(s);
        banner.setVisibility(View.VISIBLE);
        banner.setScaleX(0.6f);
        banner.setScaleY(0.6f);
        banner.animate().scaleX(1).scaleY(1).setDuration(220).start();
        h.postDelayed(() -> banner.setVisibility(View.GONE), ms);
    }

    // ================================================================== roteiro

    private void planejar() {
        float fu = meu.forca(), fo = adv.forca();
        float ment = meu.mentalidade();
        float atq = 1 + (ment - 0.48f) * 1.6f;
        float dif = m.dificuldade == 0 ? 0.8f : m.dificuldade == 2 ? 1.25f : 1f;
        int nMeu = Math.round(Math.max(2, Math.min(9, 4.6f * (float) Math.exp((fu - fo) / 22) * atq / (dif * 0.9f + 0.1f) + rnd.nextFloat() * 2 - 1)));
        int nDeles = Math.round(Math.max(1, Math.min(9, 3.8f * (float) Math.exp((fo - fu) / 22) * (1 + (ment - 0.48f) * 1.2f) * dif + rnd.nextFloat() * 2 - 1)));
        int meuGk = meu.titulares().get(0).a[5];
        convDeles = Math.max(0.12f, Math.min(0.6f, 0.3f * (float) Math.exp((fo - fu) / 30) * (1 - (meuGk - 55) / 200f) * dif));
        List<Integer> mins = new ArrayList<>();
        for (int i = 2; i <= 89; i++) if (i != 45 && i != 46) mins.add(i);
        Collections.shuffle(mins, rnd);
        int k = 0;
        for (int i = 0; i < nMeu; i++) eventos.add(new int[]{mins.get(k++), MEU});
        for (int i = 0; i < nDeles; i++) eventos.add(new int[]{mins.get(k++), DELES});
        for (int i = 0; i < 9; i++) eventos.add(new int[]{mins.get(k++), NARRA});
        Collections.sort(eventos, (a, b) -> a[0] - b[0]);
        segundosPorMinuto = 0.33f * m.minutosPartida / 4f;
    }

    private final Runnable relogio = new Runnable() {
        @Override
        public void run() {
            if (!rodando || pausado || emLance || acabou) return;
            minuto += 0.25f;
            atualizarPlacar();
            if (!intervalo && minuto >= 45) {
                intervalo = true;
                narrar("⏸️ Fim do primeiro tempo: " + meu.sigla + " " + placar[0] + " x " + placar[1] + " " + adv.sigla);
                mostrarBanner("INTERVALO", 1800);
                h.postDelayed(this, 2200);
                return;
            }
            if (minuto >= 90) {
                fimDoTempo();
                return;
            }
            while (proximo < eventos.size() && eventos.get(proximo)[0] <= minuto) {
                int[] e = eventos.get(proximo++);
                if (e[1] == MEU) {
                    abrirChance();
                    return;
                } else if (e[1] == DELES) {
                    ataqueDeles();
                    return;
                } else narrar(frase());
            }
            h.postDelayed(this, (long) (segundosPorMinuto * 250));
        }
    };

    private String frase() {
        Jogador a = sorteio(meu), b = sorteio(adv);
        String[] f = {"Troca de passes no meio-campo do " + meu.sigla + ".", b.nome + " faz falta dura em " + a.nome + ".",
                "O " + adv.sigla + " tenta sair jogando, mas erra o passe.", "Torcida canta alto nas arquibancadas! 🎶",
                "Escanteio para o " + (rnd.nextBoolean() ? meu.sigla : adv.sigla) + ", mas a zaga afasta.", a.nome + " arrisca de longe… por cima do gol.",
                "Jogo pegado no meio-campo.", "Cartão amarelo para " + b.nome + ".", a.nome + " recebe atendimento, mas volta ao jogo.",
                "O técnico do " + adv.sigla + " grita à beira do campo.", "Impedimento marcado contra o " + adv.sigla + "."};
        return f[rnd.nextInt(f.length)];
    }

    private Jogador sorteio(Clube c) {
        List<Jogador> t = c.titulares();
        return t.get(1 + rnd.nextInt(10));
    }

    private void retomar(long atraso) {
        if (acabou) return;
        h.removeCallbacks(relogio);
        h.postDelayed(relogio, atraso);
    }

    // ================================================================== lances

    private int tipoDeChance() {
        float r = rnd.nextFloat();
        return r < 0.48f ? Lance.ATAQUE : r < 0.78f ? Lance.CONTRA : r < 0.94f ? Lance.FALTA : Lance.PENALTI;
    }

    private void abrirChance() {
        emLance = true;
        chances++;
        int tipo = tipoDeChance();
        String nome = tipo == Lance.PENALTI ? "PÊNALTI para o " + meu.sigla + "!" : tipo == Lance.FALTA ? "Falta perigosa!" : tipo == Lance.CONTRA ? "Contra-ataque!" : "Chance para o " + meu.sigla + "!";
        narrar("⚡ " + nome);
        mostrarBanner("⚡ " + nome, 1100);
        h.postDelayed(() -> jogarLance(tipo), 1200);
    }

    private void jogarLance(int tipo) {
        Lance l = new Lance(rnd, meu, adv, tipo, m.dificuldade);
        cena.setLance(l, () -> fimDoLance(l));
    }

    private void fimDoLance(Lance l) {
        if (l.chuteNoGol || l.gol) chutes++;
        if (disputaPenaltis) {
            cobrancasMeu++;
            if (l.gol) penMeu++;
            cena.mostrar(l.gol ? "GOOOL!" : l.resultado, l.gol ? "" : l.detalhe, 1.8f);
            narrar(l.gol ? "✅ " + (l.autor != null ? l.autor.nome : "") + " converte!" : "❌ " + l.resultado + " " + l.detalhe);
            atualizarPlacar();
            h.postDelayed(() -> {
                cena.setLance(null, null);
                cobrancaDeles();
            }, 2200);
            return;
        }
        if (l.gol) {
            placar[0]++;
            if (l.autor != null) {
                l.autor.gols++;
                l.autor.golsCarreira++;
                autores.add("0" + l.autor.nome);
            }
            cena.mostrar("GOOOL!", l.autor != null ? l.autor.nome : "", 2.6f);
            narrar("🥅 GOOOL do " + meu.sigla + "! " + (l.autor != null ? l.autor.nome : ""));
        } else {
            cena.mostrar(l.resultado, l.detalhe, 1.8f);
            narrar("😮 " + l.resultado + (l.detalhe.isEmpty() ? "" : " " + l.detalhe));
        }
        atualizarPlacar();
        h.postDelayed(() -> {
            cena.setLance(null, null);
            emLance = false;
            retomar(500);
        }, l.gol ? 2800 : 2000);
    }

    private void ataqueDeles() {
        emLance = true;
        Jogador a = sorteioAtacante(adv);
        Jogador gk = meu.titulares().get(0);
        narrar("⚠️ O " + adv.sigla + " ataca… " + a.nome + " vai finalizar!");
        mostrarBanner("⚠️ Ataque do " + adv.sigla, 1200);
        h.postDelayed(() -> {
            if (rnd.nextFloat() < convDeles) {
                placar[1]++;
                a.gols++;
                a.golsCarreira++;
                autores.add("1" + a.nome);
                narrar("😫 Gol do " + adv.sigla + ". " + a.nome + " marca.");
                cena.mostrar("Gol do " + adv.sigla, a.nome, 2.2f);
            } else {
                String[] f = {"🧤 Grande defesa de " + gk.nome + "!", "Para fora! Que susto.", "A zaga do " + meu.sigla + " bloqueia o chute.", "Na trave! Ufa!"};
                String x = f[rnd.nextInt(f.length)];
                narrar(x);
                cena.mostrar(x.replaceAll("^[^A-Za-zÀ-ú]+", ""), "", 1.6f);
            }
            atualizarPlacar();
            emLance = false;
            retomar(1500);
        }, 1600);
    }

    private Jogador sorteioAtacante(Clube c) {
        float[] peso = {0, .5f, 1, 1.4f, 3, 5};
        List<Jogador> t = c.titulares();
        float tot = 0;
        for (Jogador j : t) tot += peso[j.pos];
        float x = rnd.nextFloat() * tot;
        for (Jogador j : t) {
            x -= peso[j.pos];
            if (x <= 0) return j;
        }
        return t.get(10);
    }

    private void pularParaLance() {
        if (emLance || acabou || pausado) return;
        while (proximo < eventos.size() && eventos.get(proximo)[1] == NARRA) proximo++;
        if (proximo < eventos.size()) {
            minuto = eventos.get(proximo)[0];
            if (minuto > 45) intervalo = true;
        } else minuto = 90;
        retomar(50);
    }

    // ================================================================== fim

    private void fimDoTempo() {
        if (mataMata && placar[0] == placar[1] && !disputaPenaltis) {
            disputaPenaltis = true;
            narrar("🎯 Empate! A vaga será decidida nos pênaltis.");
            mostrarBanner("DISPUTA DE PÊNALTIS", 2000);
            h.postDelayed(this::cobrancaMinha, 2200);
            return;
        }
        terminar();
    }

    private boolean penaltisDecididos() {
        int restaMeu = Math.max(0, 5 - cobrancasMeu), restaDeles = Math.max(0, 5 - cobrancasDeles);
        if (cobrancasMeu <= 5 && cobrancasDeles <= 5) {
            if (penMeu > penDeles + restaDeles) return true;
            if (penDeles > penMeu + restaMeu) return true;
            return cobrancasMeu == 5 && cobrancasDeles == 5 && penMeu != penDeles;
        }
        return cobrancasMeu == cobrancasDeles && penMeu != penDeles;
    }

    private void cobrancaMinha() {
        if (penaltisDecididos()) {
            terminar();
            return;
        }
        emLance = true;
        mostrarBanner("Sua cobrança " + (cobrancasMeu + 1), 900);
        h.postDelayed(() -> jogarLance(Lance.PENALTI), 1000);
    }

    private void cobrancaDeles() {
        if (penaltisDecididos()) {
            terminar();
            return;
        }
        Jogador a = sorteioAtacante(adv);
        Jogador gk = meu.titulares().get(0);
        narrar("O " + adv.sigla + " cobra com " + a.nome + "…");
        h.postDelayed(() -> {
            cobrancasDeles++;
            float p = 0.78f - (gk.a[5] - 60) / 300f;
            if (rnd.nextFloat() < p) {
                penDeles++;
                narrar("✅ " + a.nome + " converte.");
                cena.mostrar("Gol do " + adv.sigla, a.nome, 1.4f);
            } else {
                narrar("🧤 " + gk.nome + " DEFENDE!");
                cena.mostrar("DEFENDEU!", gk.nome, 1.6f);
            }
            atualizarPlacar();
            h.postDelayed(this::cobrancaMinha, 1600);
        }, 1300);
    }

    private void terminar() {
        if (acabou) return;
        acabou = true;
        h.removeCallbacksAndMessages(null);
        String fim = "Fim de jogo: " + meu.sigla + " " + placar[0] + " x " + placar[1] + " " + adv.sigla + (disputaPenaltis ? " (pênaltis " + penMeu + "x" + penDeles + ")" : "");
        narrar("🏁 " + fim);
        mostrarBanner("FIM DE JOGO", 1500);
        h.postDelayed(() -> {
            Intent r = new Intent().putExtra("meus", placar[0]).putExtra("deles", placar[1]).putStringArrayListExtra("autores", autores)
                    .putExtra("chutes", chutes).putExtra("chances", chances)
                    .putExtra("penMeu", disputaPenaltis ? penMeu : -1).putExtra("penDeles", disputaPenaltis ? penDeles : -1);
            setResult(RESULT_OK, r);
            finish();
        }, 1700);
    }

    // ================================================================== pausa / sistema

    private void pausa() {
        if (acabou || pausado) return;
        pausado = true;
        String[] op = {"▶  Continuar", "⏩  Simular o resto da partida", "🏳️  Abandonar (derrota por 3 x 0)"};
        new AlertDialog.Builder(this).setTitle("Pausa · " + meu.sigla + " " + placar[0] + " x " + placar[1] + " " + adv.sigla).setCancelable(false)
                .setItems(op, (d, w) -> {
                    pausado = false;
                    if (w == 0) {
                        if (!emLance) retomar(300);
                    } else if (w == 1) simularResto();
                    else {
                        placar[0] = 0;
                        placar[1] = Math.max(3, placar[1]);
                        autores.clear();
                        disputaPenaltis = false;
                        terminar();
                    }
                }).show();
    }

    private void simularResto() {
        cena.setLance(null, null);
        emLance = false;
        disputaPenaltis = false;
        for (int i = proximo; i < eventos.size(); i++) {
            int[] e = eventos.get(i);
            if (e[1] == MEU && rnd.nextFloat() < 0.3f) {
                placar[0]++;
                Jogador a = sorteioAtacante(meu);
                a.gols++;
                a.golsCarreira++;
                autores.add("0" + a.nome);
            } else if (e[1] == DELES && rnd.nextFloat() < convDeles) {
                placar[1]++;
                Jogador a = sorteioAtacante(adv);
                a.gols++;
                a.golsCarreira++;
                autores.add("1" + a.nome);
            }
        }
        proximo = eventos.size();
        minuto = 90;
        terminar();
    }

    @Override
    public void onBackPressed() {
        pausa();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!acabou && !pausado) pausa();
    }

    private void telaCheia() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) telaCheia();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
