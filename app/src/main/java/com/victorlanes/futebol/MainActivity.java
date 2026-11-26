package com.victorlanes.futebol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Tela inicial: continuar a carreira ou fundar um clube novo. */
public class MainActivity extends Activity {

    private Ui.Cores k;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        k = Ui.cores(this);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        inicio();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void inicio() {
        LinearLayout col = Ui.coluna(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(24), dp(40), dp(24), dp(30));
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF14532D, 0xFF0A130E, 0xFF0A130E}));
        setContentView(col);
        Ui.aplicarInsets(col);
        TextView bola = Ui.texto(this, "⚽", 80, 0xFFFFFFFF, false);
        bola.setGravity(Gravity.CENTER);
        col.addView(bola);
        TextView t = Ui.texto(this, "CRAQUE MANAGER", 32, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        TextView s = Ui.texto(this, "Técnico e presidente: monte o time, construa o estádio e conquiste o mundo", 15, 0xCCFFFFFF, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(8), 0, 0);
        col.addView(s);
        col.addView(new View(this), Ui.lp(1, 0, 1f));

        Mundo m = Sessao.mundo(this);
        if (m != null && m.eu() != null) {
            Modelo.Clube eu = m.eu();
            LinearLayout card = Ui.linha(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.fundo(0x33FFFFFF, dp(20)));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.addView(new Escudo(this).de(eu), Ui.lp(dp(52), dp(60)));
            LinearLayout tx = Ui.coluna(this);
            tx.setPadding(dp(14), 0, 0, 0);
            tx.addView(Ui.texto(this, eu.nome, 18, 0xFFFFFFFF, true));
            tx.addView(Ui.texto(this, "Temporada " + m.ano + " · " + Mundo.nomeDivisao(eu) + " · " + Modelo.reais(eu.dinheiro), 13, 0xCCFFFFFF, false));
            card.addView(tx, Ui.lp(0, -2, 1f));
            col.addView(card, Ui.lp(-1, -2));
            Button cont = Ui.botaoGradiente(this, "▶  Continuar carreira");
            cont.setTextSize(18);
            cont.setOnClickListener(v -> startActivity(new Intent(this, HubActivity.class)));
            LinearLayout.LayoutParams lp = Ui.lp(-1, dp(60));
            lp.topMargin = dp(14);
            col.addView(cont, lp);
        }
        Button novo = m != null ? Ui.botao(this, "Nova carreira", false) : Ui.botaoGradiente(this, "⚽  Fundar meu clube");
        novo.setTextSize(17);
        novo.setOnClickListener(v -> {
            if (m != null) new AlertDialog.Builder(this).setTitle("Começar de novo?")
                    .setMessage("A carreira atual será apagada.").setPositiveButton("Começar", (d, w) -> form())
                    .setNegativeButton("Cancelar", null).show();
            else form();
        });
        LinearLayout.LayoutParams lp = Ui.lp(-1, dp(56));
        lp.topMargin = dp(12);
        col.addView(novo, lp);
    }

    private int cor1 = 0xFFD32F2F, cor2 = 0xFFFFFFFF;
    private String uf = "RJ";

    private void form() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(k.fundo);
        LinearLayout col = Ui.coluna(this);
        col.setPadding(dp(20), dp(20), dp(20), dp(30));
        sv.addView(col);
        setContentView(sv);
        Ui.aplicarInsets(sv);
        col.addView(Ui.texto(this, "Fundar meu clube", 26, k.texto, true));
        col.addView(Ui.texto(this, "Você começa na Série C do seu estado, com pouco dinheiro e um estádio pequeno.", 14, k.sub, false));

        FrameLayout prev = new FrameLayout(this);
        Escudo esc = new Escudo(this);
        prev.addView(esc, new FrameLayout.LayoutParams(dp(100), dp(116), Gravity.CENTER));
        LinearLayout.LayoutParams pl = Ui.lp(-1, dp(130));
        pl.topMargin = dp(10);
        col.addView(prev, pl);

        EditText nome = campo(col, "Nome do clube", "Ex.: Estrela do Méier FC");
        EditText sig = campo(col, "Sigla (3 letras)", "Ex.: EST");
        sig.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3), new InputFilter.AllCaps()});
        sig.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        Runnable atualizar = () -> esc.de(cor1, cor2, sig.getText().length() > 0 ? sig.getText().toString() : "???");
        sig.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(android.text.Editable s) {
                atualizar.run();
            }
        });
        nome.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(android.text.Editable s) {
                if (sig.getText().length() == 0 || sig.getTag() == null) {
                    sig.setText(s.length() >= 3 ? Mundo.sigla(s.toString()) : "");
                    sig.setTag(null);
                }
            }
        });

        rotulo(col, "Estado");
        col.addView(new Ui.Chips(this, Mundo.UFS, Mundo.NOMES_UF, uf, id -> uf = id).view);
        int[] cores = {0xFFD32F2F, 0xFF1565C0, 0xFF2E7D32, 0xFF212121, 0xFFFBC02D, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F, 0xFFC2185B, 0xFFFFFFFF, 0xFF8D1C1C, 0xFF0D47A1};
        rotulo(col, "Cor principal");
        col.addView(paleta(cores, true, atualizar));
        rotulo(col, "Cor secundária");
        col.addView(paleta(cores, false, atualizar));
        atualizar.run();

        Button criar = Ui.botaoGradiente(this, "Fundar clube e começar");
        criar.setTextSize(17);
        criar.setOnClickListener(v -> {
            String n = nome.getText().toString().trim(), s = sig.getText().toString().trim().toUpperCase();
            if (n.length() < 3) {
                nome.setError("Dê um nome ao clube");
                return;
            }
            if (s.length() < 2) s = Mundo.sigla(n);
            criar.setEnabled(false);
            criar.setText("Criando o mundo do futebol…");
            final String fs = s;
            Ui.FUNDO.execute(() -> {
                Mundo m = Mundo.novo(n, fs, uf, cor1, cor2);
                m.salvar(this);
                Ui.MAIN.post(() -> {
                    Sessao.m = m;
                    startActivity(new Intent(this, HubActivity.class));
                    inicio();
                });
            });
        });
        LinearLayout.LayoutParams lp = Ui.lp(-1, dp(58));
        lp.topMargin = dp(22);
        col.addView(criar, lp);
        Button voltar = Ui.botao(this, "Voltar", false);
        voltar.setOnClickListener(v -> inicio());
        LinearLayout.LayoutParams lv = Ui.lp(-1, dp(50));
        lv.topMargin = dp(10);
        col.addView(voltar, lv);
    }

    private View paleta(int[] cores, boolean principal, Runnable depois) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(8), 0, dp(4));
        hs.addView(l);
        View[] vs = new View[cores.length];
        Runnable pintar = () -> {
            for (int i = 0; i < cores.length; i++) {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(cores[i]);
                boolean on = cores[i] == (principal ? cor1 : cor2);
                g.setStroke(dp(on ? 4 : 1), on ? k.destaque : 0x55FFFFFF);
                vs[i].setBackground(g);
            }
        };
        for (int i = 0; i < cores.length; i++) {
            int c = cores[i];
            View v = new View(this);
            v.setOnClickListener(x -> {
                if (principal) cor1 = c;
                else cor2 = c;
                pintar.run();
                depois.run();
            });
            LinearLayout.LayoutParams lp = Ui.lp(dp(42), dp(42));
            lp.rightMargin = dp(10);
            l.addView(v, lp);
            vs[i] = v;
        }
        pintar.run();
        return hs;
    }

    private void rotulo(LinearLayout col, String s) {
        TextView t = Ui.texto(this, s, 13, k.sub, true);
        t.setPadding(dp(2), dp(16), 0, dp(4));
        col.addView(t);
    }

    private EditText campo(LinearLayout col, String r, String dica) {
        rotulo(col, r);
        EditText e = new EditText(this);
        e.setHint(dica);
        e.setSingleLine(true);
        e.setTextColor(k.texto);
        e.setHintTextColor(0x88FFFFFF);
        e.setTextSize(17);
        e.setBackground(Ui.fundo(k.superficie, dp(14)));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        col.addView(e, Ui.lp(-1, -2));
        return e;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Sessao.m != null && getWindow().getDecorView().findFocus() == null) {
            // volta do jogo: atualiza o cartão da carreira
        }
    }
}
