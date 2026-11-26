package com.victorlanes.futebol;

import android.content.Context;

/** Mundo carregado em memória enquanto o app está aberto. */
public final class Sessao {
    private Sessao() {
    }

    public static Mundo m;

    public static Mundo mundo(Context c) {
        if (m == null && Mundo.existe(c)) m = Mundo.carregar(c);
        return m;
    }

    public static void salvar(Context c) {
        if (m != null) m.salvar(c.getApplicationContext());
    }
}
