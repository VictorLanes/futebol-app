package com.victorlanes.futebol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.ArrayList;

/** Partida jogada com joystick. Devolve o placar e os autores dos gols. */
public class PartidaActivity extends Activity {

    private Jogo jogo;
    private JogoView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Mundo m = Sessao.mundo(this);
        if (m == null) {
            finish();
            return;
        }
        Modelo.Clube adv = m.clube(getIntent().getIntExtra("adv", 0));
        if (adv == null) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28)
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        jogo = new Jogo(m.eu(), adv, m.minutosPartida, m.dificuldade);
        view = new JogoView(this, jogo, this::terminar, this::pausa);
        setContentView(view);
        telaCheia();
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
    protected void onPause() {
        super.onPause();
        if (view != null && jogo.estado != Jogo.FIM) view.setPausado(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null && jogo.estado != Jogo.FIM && !dialogoAberto) pausa();
    }

    private boolean dialogoAberto;

    private void pausa() {
        if (dialogoAberto || jogo.estado == Jogo.FIM) return;
        if (jogo.tempoReal < 0.5f) return; // início da partida: não pausa
        dialogoAberto = true;
        view.setPausado(true);
        String placar = jogo.clubes[0].sigla + " " + jogo.placar[0] + " x " + jogo.placar[1] + " " + jogo.clubes[1].sigla + " · " + jogo.minuto() + "'";
        String[] op = {"▶  Continuar", "⏩  Simular o resto da partida", "🏳️  Abandonar (derrota por 3 x 0)"};
        new AlertDialog.Builder(this).setTitle("Pausa · " + placar).setCancelable(false).setItems(op, (d, w) -> {
            dialogoAberto = false;
            if (w == 0) view.setPausado(false);
            else if (w == 1) simularResto();
            else {
                jogo.placar[0] = 0;
                jogo.placar[1] = Math.max(3, jogo.placar[1]);
                jogo.autores.clear();
                terminar();
            }
        }).show();
    }

    @Override
    public void onBackPressed() {
        pausa();
    }

    private void simularResto() {
        Mundo m = Sessao.m;
        float resto = 1 - jogo.tempoReal / jogo.duracao;
        double fa = jogo.clubes[0].forca(), fb = jogo.clubes[1].forca();
        int ga = (int) m.poisson(1.3 * Math.exp((fa - fb) / 13) * resto), gb = (int) m.poisson(1.2 * Math.exp((fb - fa) / 13) * resto);
        for (int i = 0; i < ga; i++) {
            jogo.placar[0]++;
            jogo.autores.add("0" + m.autor(jogo.clubes[0], true).substring(1));
        }
        for (int i = 0; i < gb; i++) {
            jogo.placar[1]++;
            jogo.autores.add("1" + m.autor(jogo.clubes[1], false).substring(1));
        }
        terminar();
    }

    private boolean terminou;

    private void terminar() {
        if (terminou) return;
        terminou = true;
        ArrayList<String> a = new ArrayList<>(jogo.autores);
        Intent r = new Intent().putExtra("meus", jogo.placar[0]).putExtra("deles", jogo.placar[1]).putStringArrayListExtra("autores", a)
                .putExtra("chutes0", jogo.chutes[0]).putExtra("chutes1", jogo.chutes[1])
                .putExtra("posse", Math.round(100 * jogo.posse[0] / Math.max(1, jogo.posse[0] + jogo.posse[1])));
        setResult(RESULT_OK, r);
        finish();
    }
}
