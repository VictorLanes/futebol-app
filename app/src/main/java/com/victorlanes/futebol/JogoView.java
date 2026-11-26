package com.victorlanes.futebol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Desenha a partida (câmera seguindo a bola) e lê o joystick e os botões na tela. */
public class JogoView extends View {

    public interface AoTerminar {
        void fim();
    }

    public interface AoPausar {
        void pausar();
    }

    private final Jogo j;
    private final AoTerminar aoTerminar;
    private final AoPausar aoPausar;
    private long ultimo;
    private boolean avisado, pausado;
    private float cx = Jogo.L / 2, cy = Jogo.W / 2, esc = 20; // câmera (metros) e pixels por metro
    private final float d;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), linha = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    // controles
    private float joyX, joyY, joyR; // centro do joystick (px)
    private int joyId = -1;
    private float joyTx, joyTy;
    private final float[][] bt = new float[4][3]; // chute, passe, correr, pausa: x, y, raio
    private final int[] btId = {-1, -1, -1, -1};
    private long passeInicio;

    public JogoView(Context c, Jogo jogo, AoTerminar fim, AoPausar pausa) {
        super(c);
        j = jogo;
        aoTerminar = fim;
        aoPausar = pausa;
        d = getResources().getDisplayMetrics().density;
        linha.setStyle(Paint.Style.STROKE);
        linha.setColor(0xDDFFFFFF);
        txt.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        txt.setTextAlign(Paint.Align.CENTER);
        setKeepScreenOn(true);
    }

    public void setPausado(boolean v) {
        pausado = v;
        ultimo = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        esc = Math.max(w / 46f, h / 30f);
        joyR = Math.min(h * 0.17f, 80 * d);
        joyX = joyR + 30 * d;
        joyY = h - joyR - 26 * d;
        float br = Math.min(h * 0.12f, 50 * d);
        bt[0] = new float[]{w - br - 26 * d, h - br - 30 * d, br * 1.15f}; // chute
        bt[1] = new float[]{w - br * 3.3f - 26 * d, h - br - 18 * d, br * 0.95f}; // passe
        bt[2] = new float[]{w - br - 30 * d, h - br * 3.5f - 30 * d, br * 0.85f}; // correr
        bt[3] = new float[]{30 * d, 30 * d, 22 * d}; // pausa
    }

    // ================================================================== laço

    @Override
    protected void onDraw(Canvas c) {
        long agora = System.nanoTime();
        float dt = ultimo == 0 ? 0.016f : Math.min(0.05f, (agora - ultimo) / 1e9f);
        ultimo = agora;
        if (!pausado) {
            if (j.cargaChute >= 0) j.cargaChute = Math.min(1.2f, j.cargaChute + dt * 1.1f);
            float sub = dt / 2;
            j.passo(sub);
            j.passo(sub);
        }
        desenhar(c);
        if (j.estado == Jogo.FIM && !avisado) {
            avisado = true;
            postDelayed(aoTerminar::fim, 1500);
        }
        if (!pausado) postInvalidateOnAnimation();
    }

    float sx(float x) {
        return (x - cx) * esc + getWidth() / 2f;
    }

    float sy(float y) {
        return (y - cy) * esc + getHeight() / 2f;
    }

    void desenhar(Canvas c) {
        int w = getWidth(), h = getHeight();
        // câmera
        float alvoX = j.bola.x + j.bola.vx * 0.25f, alvoY = j.bola.y + j.bola.vy * 0.15f;
        cx += (alvoX - cx) * 0.08f;
        cy += (alvoY - cy) * 0.08f;
        float mw = w / esc / 2, mh = h / esc / 2;
        cx = Math.max(mw - 6, Math.min(Jogo.L - mw + 6, cx));
        cy = Math.max(mh - 5, Math.min(Jogo.W - mh + 5, cy));

        c.drawColor(0xFF143D22);
        // arquibancada fora do campo
        p.setColor(0xFF0F2E1A);
        c.drawRect(sx(-8), sy(-8), sx(Jogo.L + 8), sy(Jogo.W + 8), p);
        // gramado com faixas
        for (int i = 0; i < 20; i++) {
            p.setColor(i % 2 == 0 ? 0xFF2E8B3A : 0xFF339A41);
            c.drawRect(sx(i * Jogo.L / 20), sy(-3), sx((i + 1) * Jogo.L / 20), sy(Jogo.W + 3), p);
        }
        // linhas
        linha.setStrokeWidth(Math.max(2, 0.12f * esc));
        r.set(sx(0), sy(0), sx(Jogo.L), sy(Jogo.W));
        c.drawRect(r, linha);
        c.drawLine(sx(Jogo.L / 2), sy(0), sx(Jogo.L / 2), sy(Jogo.W), linha);
        c.drawCircle(sx(Jogo.L / 2), sy(Jogo.W / 2), 9.15f * esc, linha);
        p.setColor(0xDDFFFFFF);
        c.drawCircle(sx(Jogo.L / 2), sy(Jogo.W / 2), 0.3f * esc, p);
        for (int lado = 0; lado < 2; lado++) {
            float g = lado == 0 ? 0 : Jogo.L, s = lado == 0 ? 1 : -1;
            r.set(Math.min(sx(g), sx(g + s * 16.5f)), sy(34 - 20.16f), Math.max(sx(g), sx(g + s * 16.5f)), sy(34 + 20.16f));
            c.drawRect(r, linha);
            r.set(Math.min(sx(g), sx(g + s * 5.5f)), sy(34 - 9.16f), Math.max(sx(g), sx(g + s * 5.5f)), sy(34 + 9.16f));
            c.drawRect(r, linha);
            c.drawCircle(sx(g + s * 11), sy(34), 0.25f * esc, p);
            r.set(sx(g + s * 11) - 9.15f * esc, sy(34) - 9.15f * esc, sx(g + s * 11) + 9.15f * esc, sy(34) + 9.15f * esc);
            c.drawArc(r, lado == 0 ? -53 : 127, 106, false, linha);
            // gol
            p.setColor(0x55FFFFFF);
            r.set(Math.min(sx(g), sx(g - s * 2)), sy(Jogo.GOL_Y1), Math.max(sx(g), sx(g - s * 2)), sy(Jogo.GOL_Y2));
            c.drawRect(r, p);
            Paint rede = new Paint(linha);
            rede.setStrokeWidth(1);
            rede.setColor(0x66FFFFFF);
            for (float y = Jogo.GOL_Y1; y <= Jogo.GOL_Y2; y += 0.6f) c.drawLine(r.left, sy(y), r.right, sy(y), rede);
            Paint trave = new Paint(linha);
            trave.setColor(0xFFFFFFFF);
            trave.setStrokeWidth(Math.max(3, 0.2f * esc));
            c.drawRect(r, trave);
            p.setColor(0xDDFFFFFF);
        }
        // sombras dos jogadores e da bola
        p.setColor(0x44000000);
        for (Jogo.P q : j.ps) c.drawOval(sx(q.x) - 0.75f * esc, sy(q.y) - 0.35f * esc + 0.35f * esc, sx(q.x) + 0.85f * esc, sy(q.y) + 0.55f * esc, p);
        c.drawOval(sx(j.bola.x) - 0.35f * esc, sy(j.bola.y) - 0.2f * esc, sx(j.bola.x) + 0.35f * esc, sy(j.bola.y) + 0.2f * esc, p);

        // jogadores
        for (Jogo.P q : j.ps) desenharJogador(c, q);

        // bola (sobe com a altura)
        float bx = sx(j.bola.x), by = sy(j.bola.y) - j.bola.z * esc * 0.55f, br = (0.33f + j.bola.z * 0.03f) * esc;
        p.setShader(new RadialGradient(bx - br * .3f, by - br * .3f, br * 1.3f, 0xFFFFFFFF, 0xFFBFC5C8, Shader.TileMode.CLAMP));
        c.drawCircle(bx, by, br, p);
        p.setShader(null);
        p.setColor(0xFF222222);
        c.drawCircle(bx + br * .15f, by + br * .1f, br * .35f, p);

        // barra de força do chute
        if (j.cargaChute >= 0 && j.controlado != null) {
            float px = sx(j.controlado.x), py = sy(j.controlado.y) - 2.4f * esc;
            float lw = 3 * esc, f = Math.min(1, j.cargaChute);
            p.setColor(0xAA000000);
            c.drawRoundRect(px - lw / 2, py, px + lw / 2, py + 0.5f * esc, 6, 6, p);
            p.setColor(f > 0.85f ? 0xFFEF4444 : f > 0.55f ? 0xFFF59E0B : 0xFF4ADE80);
            c.drawRoundRect(px - lw / 2, py, px - lw / 2 + lw * f, py + 0.5f * esc, 6, 6, p);
        }

        hud(c, w, h);
        controles(c);
    }

    void desenharJogador(Canvas c, Jogo.P q) {
        Modelo.Clube cl = j.clubes[q.time];
        int cor = q.goleiro ? (q.time == 0 ? 0xFFFACC15 : 0xFF22D3EE) : cl.cor1;
        int cor2 = q.goleiro ? 0xFF111111 : cl.cor2;
        if (q.time == 1 && !q.goleiro && parecido(j.clubes[0].cor1, cl.cor1)) {
            cor = cl.cor2;
            cor2 = cl.cor1;
        }
        float x = sx(q.x), y = sy(q.y), rr = 0.78f * esc;
        if (q == j.controlado) {
            Paint an = new Paint(Paint.ANTI_ALIAS_FLAG);
            an.setStyle(Paint.Style.STROKE);
            an.setStrokeWidth(0.18f * esc);
            an.setColor(0xFFFFEB3B);
            c.drawCircle(x, y, rr * 1.55f, an);
            // seta indicando direção
            p.setColor(0xFFFFEB3B);
            android.graphics.Path t = new android.graphics.Path();
            float ax = x + q.fx * rr * 2.2f, ay = y + q.fy * rr * 2.2f;
            t.moveTo(ax + q.fx * rr * .7f, ay + q.fy * rr * .7f);
            t.lineTo(ax - q.fy * rr * .45f, ay + q.fx * rr * .45f);
            t.lineTo(ax + q.fy * rr * .45f, ay - q.fx * rr * .45f);
            t.close();
            c.drawPath(t, p);
        }
        if (q.carrinho > 0) {
            p.setColor(0x55FFFFFF);
            c.drawOval(x - rr * 1.6f, y - rr * .7f, x + rr * 1.6f, y + rr * .7f, p);
        }
        p.setShader(new LinearGradient(x, y - rr, x, y + rr, clarear(cor), cor, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr, p);
        p.setShader(null);
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setStyle(Paint.Style.STROKE);
        b.setStrokeWidth(0.14f * esc);
        b.setColor(cor2);
        c.drawCircle(x, y, rr, b);
        txt.setTextSize(0.85f * esc);
        txt.setColor(cor2);
        c.drawText(String.valueOf(q.j.numero), x, y + 0.3f * esc, txt);
        if (q == j.controlado || j.bola.dono == q) {
            txt.setTextSize(Math.max(11 * d, 0.7f * esc));
            txt.setColor(0xFFFFFFFF);
            txt.setShadowLayer(4, 0, 1, 0xCC000000);
            c.drawText(q.j.nome, x, y - rr * 1.9f, txt);
            txt.clearShadowLayer();
        }
    }

    static boolean parecido(int a, int b) {
        int dr = Color.red(a) - Color.red(b), dg = Color.green(a) - Color.green(b), db = Color.blue(a) - Color.blue(b);
        return dr * dr + dg * dg + db * db < 9000;
    }

    static int clarear(int c) {
        return Color.rgb(Math.min(255, Color.red(c) + 50), Math.min(255, Color.green(c) + 50), Math.min(255, Color.blue(c) + 50));
    }

    void hud(Canvas c, int w, int h) {
        // placar
        float pw = 300 * d, ph = 40 * d, px = w / 2f - pw / 2, py = 10 * d;
        p.setColor(0xDD0B1410);
        c.drawRoundRect(px, py, px + pw, py + ph, 14 * d, 14 * d, p);
        for (int t = 0; t < 2; t++) {
            p.setColor(j.clubes[t].cor1);
            float bx = t == 0 ? px + 10 * d : px + pw - 22 * d;
            c.drawRoundRect(bx, py + 12 * d, bx + 12 * d, py + ph - 12 * d, 3 * d, 3 * d, p);
        }
        txt.setColor(0xFFFFFFFF);
        txt.setTextSize(15 * d);
        c.drawText(j.clubes[0].sigla, px + 56 * d, py + 26 * d, txt);
        c.drawText(j.clubes[1].sigla, px + pw - 56 * d, py + 26 * d, txt);
        txt.setTextSize(20 * d);
        c.drawText(j.placar[0] + "  -  " + j.placar[1], w / 2f, py + 27 * d, txt);
        txt.setTextSize(12 * d);
        txt.setColor(0xFF4ADE80);
        c.drawText(j.minuto() + "'" + (j.segundoTempo ? " 2ºT" : " 1ºT"), w / 2f, py + ph + 16 * d, txt);

        // mini mapa
        float mw = 120 * d, mh = mw * Jogo.W / Jogo.L, mx = w - mw - 14 * d, my = 10 * d;
        p.setColor(0x9919612B);
        c.drawRoundRect(mx, my, mx + mw, my + mh, 6 * d, 6 * d, p);
        Paint ml = new Paint(linha);
        ml.setStrokeWidth(1);
        ml.setColor(0x88FFFFFF);
        c.drawLine(mx + mw / 2, my, mx + mw / 2, my + mh, ml);
        for (Jogo.P q : j.ps) {
            p.setColor(q == j.controlado ? 0xFFFFEB3B : q.time == 0 ? j.clubes[0].cor1 : (parecido(j.clubes[0].cor1, j.clubes[1].cor1) ? j.clubes[1].cor2 : j.clubes[1].cor1));
            c.drawCircle(mx + q.x / Jogo.L * mw, my + q.y / Jogo.W * mh, 2.6f * d, p);
        }
        p.setColor(0xFFFFFFFF);
        c.drawCircle(mx + j.bola.x / Jogo.L * mw, my + j.bola.y / Jogo.W * mh, 2.2f * d, p);

        // aviso no meio
        if (j.avisoT > 0 && j.aviso != null) {
            boolean gol = j.aviso.startsWith("GOOOL");
            txt.setTextSize((gol ? 40 : 24) * d);
            txt.setColor(gol ? 0xFFFFEB3B : 0xFFFFFFFF);
            txt.setShadowLayer(8, 0, 2, 0xCC000000);
            c.drawText(j.aviso, w / 2f, h * 0.36f, txt);
            txt.clearShadowLayer();
        }
    }

    void controles(Canvas c) {
        // joystick
        p.setColor(0x33FFFFFF);
        c.drawCircle(joyX, joyY, joyR, p);
        Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
        b.setStyle(Paint.Style.STROKE);
        b.setStrokeWidth(2 * d);
        b.setColor(0x66FFFFFF);
        c.drawCircle(joyX, joyY, joyR, b);
        p.setColor(0xAAFFFFFF);
        c.drawCircle(joyX + j.jx * joyR * 0.6f, joyY + j.jy * joyR * 0.6f, joyR * 0.42f, p);

        boolean atq = j.ataque() || (j.bola.dono == null && j.controlado != null && Jogo.dist(j.controlado, j.bola.x, j.bola.y) < 2);
        String[] nomes = {atq ? "CHUTE" : "CARRINHO", atq ? "PASSE" : "TROCAR", "CORRER", "II"};
        int[] cores = {atq ? 0xCCE11D48 : 0xCCEA580C, atq ? 0xCC16A34A : 0xCC2563EB, 0xCC475569, 0x88000000};
        for (int i = 0; i < 4; i++) {
            boolean ap = btId[i] >= 0;
            p.setColor(ap ? (cores[i] | 0xFF000000) : cores[i]);
            c.drawCircle(bt[i][0], bt[i][1], bt[i][2] * (ap ? 0.94f : 1), p);
            b.setColor(0x88FFFFFF);
            c.drawCircle(bt[i][0], bt[i][1], bt[i][2] * (ap ? 0.94f : 1), b);
            txt.setColor(0xFFFFFFFF);
            txt.setTextSize((i == 3 ? 14 : nomes[i].length() > 6 ? 11 : 13) * d);
            c.drawText(nomes[i], bt[i][0], bt[i][1] + 5 * d, txt);
        }
        if (atq) {
            txt.setTextSize(10 * d);
            txt.setColor(0xAAFFFFFF);
            c.drawText("segure = lançamento", bt[1][0], bt[1][1] + bt[1][2] + 14 * d, txt);
            c.drawText("segure p/ força", bt[0][0], bt[0][1] - bt[0][2] - 8 * d, txt);
        }
    }

    // ================================================================== toque

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                apertar(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++)
                    if (e.getPointerId(i) == joyId) mover(e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                soltar(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                joyId = -1;
                j.jx = j.jy = 0;
                for (int i = 0; i < 4; i++) btId[i] = -1;
                j.correr = false;
                j.cargaChute = -1;
                break;
        }
        return true;
    }

    void apertar(int id, float x, float y) {
        for (int i = 0; i < 4; i++) {
            if (Math.hypot(x - bt[i][0], y - bt[i][1]) < bt[i][2] * 1.25f) {
                btId[i] = id;
                boolean atq = j.ataque() || (j.bola.dono == null && j.controlado != null && Jogo.dist(j.controlado, j.bola.x, j.bola.y) < 2);
                if (i == 0) {
                    if (atq) j.cargaChute = 0;
                    else j.carrinho();
                } else if (i == 1) {
                    if (atq) passeInicio = System.currentTimeMillis();
                    else {
                        j.trocar();
                        passeInicio = 0;
                    }
                } else if (i == 2) j.correr = true;
                else if (i == 3 && aoPausar != null) aoPausar.pausar();
                vibrar();
                return;
            }
        }
        if (x < getWidth() * 0.5f && joyId < 0) {
            joyId = id;
            // joystick flutuante: centro onde o dedo tocou (perto do canto)
            if (Math.hypot(x - joyX, y - joyY) > joyR * 1.6f) {
                joyX = Math.max(joyR + 10 * d, Math.min(getWidth() * 0.45f, x));
                joyY = Math.max(getHeight() * 0.45f, Math.min(getHeight() - joyR - 10 * d, y));
            }
            mover(x, y);
        }
    }

    void mover(float x, float y) {
        float dx = (x - joyX) / joyR, dy = (y - joyY) / joyR, m = (float) Math.hypot(dx, dy);
        if (m > 1) {
            dx /= m;
            dy /= m;
        }
        j.jx = dx;
        j.jy = dy;
    }

    void soltar(int id) {
        if (id == joyId) {
            joyId = -1;
            j.jx = j.jy = 0;
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (btId[i] != id) continue;
            btId[i] = -1;
            if (i == 0 && j.cargaChute >= 0) j.soltarChute();
            else if (i == 1 && passeInicio > 0) {
                if (System.currentTimeMillis() - passeInicio > 330) j.lancamento();
                else j.passe();
                passeInicio = 0;
            } else if (i == 2) j.correr = false;
        }
    }

    void vibrar() {
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
    }
}
