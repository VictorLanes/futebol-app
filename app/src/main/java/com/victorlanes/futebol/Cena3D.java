package com.victorlanes.futebol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import com.victorlanes.futebol.Modelo.Clube;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Estádio em perspectiva 3D (câmera atrás da bola). Mostra o lance e lê o gesto de arrastar:
 * o ponto onde o dedo termina é o alvo (no gramado ou na face do gol), a velocidade do gesto é a força
 * e o arco do gesto é a curva.
 */
public class Cena3D extends View {

    public interface Ouvinte {
        void chutou();
    }

    private final float d;
    private Lance lance;
    private Ouvinte ouvinte;
    private Clube[] clubes = new Clube[2];
    private long ultimo;
    public String mensagem, sub;
    public float mensagemT;

    // câmera
    private float cx = 52, cy = -30, cz = 25, tx = 52, ty = 34, tz = 0;
    private float fX, fY, fZ, rX, rY, rZ, uX, uY, uZ, foco;
    private int W, H;

    // modo ambiente (entre os lances)
    private final List<Lance.Ator> ambiente = new ArrayList<>();
    private float abx = 52.5f, aby = 34, abz = 0.11f, aAlvoX = 52.5f, aAlvoY = 34, aTimer;
    private int aPosse;
    private final Random rnd = new Random();

    // estádio
    private float[] torcida; // x, y, z, cor
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), s = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    // gesto
    private boolean arrastando;
    private final List<float[]> trilha = new ArrayList<>();
    private long inicioGesto;
    private float[] previa;
    private float alvoX, alvoY, alvoZ;
    private boolean alvoGol;

    public Cena3D(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeCap(Paint.Cap.ROUND);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        gerarTorcida();
        setKeepScreenOn(true);
    }

    public void clubes(Clube meu, Clube adv) {
        clubes[0] = meu;
        clubes[1] = adv;
        montarAmbiente();
    }

    public void setLance(Lance l, Ouvinte o) {
        lance = l;
        ouvinte = o;
        arrastando = false;
        previa = null;
        invalidate();
    }

    public Lance lance() {
        return lance;
    }

    public void mostrar(String m, String sb, float tempo) {
        mensagem = m;
        sub = sb;
        mensagemT = tempo;
    }

    // ================================================================== torcida e ambiente

    private void gerarTorcida() {
        Random r = new Random(7);
        int n = 2400;
        torcida = new float[n * 4];
        int[] cores = {0xFFE11D48, 0xFF2563EB, 0xFFFACC15, 0xFFFFFFFF, 0xFF16A34A, 0xFFF97316, 0xFF111827, 0xFF9333EA, 0xFF64748B};
        for (int i = 0; i < n; i++) {
            float x, y, z;
            int lado = i % 4;
            float u = r.nextFloat(), v = r.nextFloat();
            if (lado == 0) { // atrás do gol adversário
                x = Lance.L + 9 + v * 16;
                y = -12 + u * 92;
            } else if (lado == 1) { // lateral esquerda
                x = -6 + u * 117;
                y = -9 - v * 16;
            } else if (lado == 2) { // lateral direita
                x = -6 + u * 117;
                y = Lance.W + 9 + v * 16;
            } else { // atrás do meu gol
                x = -9 - v * 16;
                y = -12 + u * 92;
            }
            z = 0.8f + v * 13;
            torcida[i * 4] = x;
            torcida[i * 4 + 1] = y;
            torcida[i * 4 + 2] = z;
            torcida[i * 4 + 3] = cores[r.nextInt(cores.length)];
        }
    }

    private void montarAmbiente() {
        ambiente.clear();
        for (int t = 0; t < 2; t++) {
            Clube c = clubes[t];
            if (c == null) return;
            float[][] v = c.vagas();
            List<Modelo.Jogador> tit = c.titulares();
            for (int i = 0; i < 11; i++) {
                Lance.Ator a = new Lance.Ator();
                a.time = t;
                a.j = tit.get(i);
                a.numero = a.j.numero;
                a.goleiro = i == 0;
                float x = 3 + v[i][0] * 48, y = 4 + v[i][1] * 60;
                a.x = a.ax = t == 0 ? x : Lance.L - x;
                a.y = a.ay = t == 0 ? y : Lance.W - y;
                a.fase = rnd.nextFloat() * 6;
                a.vel = 60;
                ambiente.add(a);
            }
        }
    }

    private void passoAmbiente(float dt) {
        aTimer -= dt;
        if (aTimer <= 0) {
            aTimer = 0.8f + rnd.nextFloat() * 1.4f;
            if (rnd.nextFloat() < 0.25f) aPosse = 1 - aPosse;
            List<Lance.Ator> l = new ArrayList<>();
            for (Lance.Ator a : ambiente) if (a.time == aPosse && !a.goleiro) l.add(a);
            if (!l.isEmpty()) {
                Lance.Ator a = l.get(rnd.nextInt(l.size()));
                aAlvoX = a.x;
                aAlvoY = a.y;
            }
        }
        abx += (aAlvoX - abx) * Math.min(1, dt * 2.5f);
        aby += (aAlvoY - aby) * Math.min(1, dt * 2.5f);
        for (Lance.Ator a : ambiente) {
            float desloc = (abx - 52.5f) * 0.45f;
            float bx = a.ax + desloc, by = a.ay + (aby - 34) * 0.15f;
            if (a.goleiro) bx = a.ax;
            float dx = bx - a.x, dy = by - a.y, dd = (float) Math.hypot(dx, dy);
            float v = Math.min(5, dd * 1.2f);
            a.vx = dd > 0.1f ? dx / dd * v : 0;
            a.vy = dd > 0.1f ? dy / dd * v : 0;
            a.x += a.vx * dt;
            a.y += a.vy * dt;
            a.fase += Math.hypot(a.vx, a.vy) * dt * 2.2f;
        }
    }

    // ================================================================== câmera / projeção

    private void base() {
        fX = tx - cx;
        fY = ty - cy;
        fZ = tz - cz;
        float n = (float) Math.sqrt(fX * fX + fY * fY + fZ * fZ);
        fX /= n;
        fY /= n;
        fZ /= n;
        // direita = frente x cima(0,0,1)
        rX = fY;
        rY = -fX;
        rZ = 0;
        n = (float) Math.sqrt(rX * rX + rY * rY);
        rX /= n;
        rY /= n;
        // cima = direita x frente
        uX = rY * fZ - rZ * fY;
        uY = rZ * fX - rX * fZ;
        uZ = rX * fY - rY * fX;
        foco = (W / 2f) / (float) Math.tan(Math.toRadians(30));
    }

    /** espaço da câmera: {lado, altura, profundidade} */
    private float[] cam(float x, float y, float z) {
        float dx = x - cx, dy = y - cy, dz = z - cz;
        return new float[]{dx * rX + dy * rY + dz * rZ, dx * uX + dy * uY + dz * uZ, dx * fX + dy * fY + dz * fZ};
    }

    private float px(float[] c) {
        return W / 2f + c[0] / c[2] * foco;
    }

    private float py(float[] c) {
        return H / 2f - c[1] / c[2] * foco;
    }

    private static final float PERTO = 0.4f;

    /** polígono no mundo, recortado no plano próximo */
    private void poligono(Canvas c, float[] xyz, Paint pt) {
        int n = xyz.length / 3;
        List<float[]> in = new ArrayList<>();
        for (int i = 0; i < n; i++) in.add(cam(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]));
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            float[] a = in.get(i), b = in.get((i + 1) % in.size());
            boolean ai = a[2] > PERTO, bi = b[2] > PERTO;
            if (ai) out.add(a);
            if (ai != bi) {
                float k = (PERTO - a[2]) / (b[2] - a[2]);
                out.add(new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, PERTO});
            }
        }
        if (out.size() < 3) return;
        path.reset();
        for (int i = 0; i < out.size(); i++) {
            float[] q = out.get(i);
            if (i == 0) path.moveTo(px(q), py(q));
            else path.lineTo(px(q), py(q));
        }
        path.close();
        c.drawPath(path, pt);
    }

    private void linha(Canvas c, float x1, float y1, float z1, float x2, float y2, float z2, float larguraM, int cor) {
        float[] a = cam(x1, y1, z1), b = cam(x2, y2, z2);
        if (a[2] < PERTO && b[2] < PERTO) return;
        if (a[2] < PERTO) {
            float k = (PERTO - a[2]) / (b[2] - a[2]);
            a = new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, PERTO};
        } else if (b[2] < PERTO) {
            float k = (PERTO - b[2]) / (a[2] - b[2]);
            b = new float[]{b[0] + (a[0] - b[0]) * k, b[1] + (a[1] - b[1]) * k, PERTO};
        }
        float prof = (a[2] + b[2]) / 2;
        s.setStrokeWidth(Math.max(1f, larguraM * foco / prof));
        s.setColor(cor);
        c.drawLine(px(a), py(a), px(b), py(b), s);
    }

    private void circuloChao(Canvas c, float x0, float y0, float r, float a0, float a1, int cor) {
        int n = 36;
        for (int i = 0; i < n; i++) {
            double t0 = a0 + (a1 - a0) * i / n, t1 = a0 + (a1 - a0) * (i + 1) / n;
            linha(c, x0 + r * (float) Math.cos(t0), y0 + r * (float) Math.sin(t0), 0.01f, x0 + r * (float) Math.cos(t1), y0 + r * (float) Math.sin(t1), 0.01f, 0.12f, cor);
        }
    }

    // ================================================================== laço

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
    }

    @Override
    protected void onDraw(Canvas c) {
        long agora = System.nanoTime();
        float dt = ultimo == 0 ? 0.016f : Math.min(0.05f, (agora - ultimo) / 1e9f);
        ultimo = agora;
        if (lance != null) {
            int antes = lance.estado;
            for (int i = 0; i < 3; i++) lance.passo(dt / 3);
            if (antes != Lance.FIM && lance.estado == Lance.FIM && ouvinte != null) post(ouvinte::chutou);
        } else passoAmbiente(dt);
        if (mensagemT > 0) mensagemT -= dt;
        moverCamera(dt);
        base();
        desenhar(c);
        postInvalidateOnAnimation();
    }

    private float dirX = 1, dirY = 0;

    private void moverCamera(float dt) {
        float dcx, dcy, dcz, dtx, dty, dtz;
        if (lance == null) {
            float pan = (abx - 52.5f) * 0.6f;
            dcx = 52.5f + pan;
            dcy = -34;
            dcz = 27;
            dtx = 52.5f + pan;
            dty = 30;
            dtz = 0;
        } else {
            float bx = lance.bx, by = lance.by;
            if (lance.estado == Lance.ESPERANDO) {
                float gx = Lance.L - bx, gy = 34 - by, n = (float) Math.hypot(gx, gy);
                dirX = gx / n;
                dirY = gy / n;
            }
            float atras = lance.tipo == Lance.PENALTI ? 7 : 9.5f, alto = lance.tipo == Lance.PENALTI ? 3.2f : 4.4f;
            if (lance.estado == Lance.VOANDO && lance.chuteNoGol) {
                dcx = cx;
                dcy = cy;
                dcz = cz;
                dtx = tx;
                dty = ty;
                dtz = tz;
            } else {
                dcx = bx - dirX * atras;
                dcy = by - dirY * atras;
                dcz = alto + lance.bz * 0.3f;
                dtx = bx + dirX * 14;
                dty = by + dirY * 14;
                dtz = 0.4f;
            }
        }
        float k = Math.min(1, dt * (lance != null && lance.estado == Lance.VOANDO ? 2.2f : 3.5f));
        cx += (dcx - cx) * k;
        cy += (dcy - cy) * k;
        cz += (dcz - cz) * k;
        tx += (dtx - tx) * k;
        ty += (dty - ty) * k;
        tz += (dtz - tz) * k;
    }

    // ================================================================== desenho

    private void desenhar(Canvas c) {
        // céu noturno
        p.setShader(new LinearGradient(0, 0, 0, H, new int[]{0xFF0B1B3A, 0xFF16325C, 0xFF0D2236}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        arquibancadas(c);
        gramado(c);
        gol(c, Lance.L, 1);
        gol(c, 0, -1);

        // atores + bola por profundidade
        List<Object[]> ordem = new ArrayList<>();
        List<Lance.Ator> lista = lance != null ? lance.atores : ambiente;
        for (Lance.Ator a : lista) ordem.add(new Object[]{cam(a.x, a.y, 0)[2], a});
        float bx = lance != null ? lance.bx : abx, by = lance != null ? lance.by : aby, bz = lance != null ? lance.bz : abz;
        ordem.add(new Object[]{cam(bx, by, bz)[2], "bola"});
        Collections.sort(ordem, (a, b) -> Float.compare((float) b[0], (float) a[0]));
        // sombras primeiro
        for (Object[] o : ordem) {
            if (o[1] instanceof Lance.Ator) sombra(c, ((Lance.Ator) o[1]).x, ((Lance.Ator) o[1]).y, 0.42f);
            else sombra(c, bx, by, 0.14f + bz * 0.02f);
        }
        if (lance != null && lance.estado == Lance.ESPERANDO && lance.portador != null) anelPortador(c);
        if (arrastando && previa != null) guia(c);
        for (Object[] o : ordem) {
            if (o[1] instanceof Lance.Ator) jogador(c, (Lance.Ator) o[1]);
            else bola(c, bx, by, bz);
        }
        hud(c);
    }

    private void arquibancadas(Canvas c) {
        int corArq = 0xFF1E2A3A, corArq2 = 0xFF283849;
        // atrás do gol adversário (degraus subindo)
        p.setColor(corArq);
        poligono(c, new float[]{Lance.L + 8, -14, 0, Lance.L + 8, Lance.W + 14, 0, Lance.L + 26, Lance.W + 14, 15, Lance.L + 26, -14, 15}, p);
        p.setColor(corArq2);
        poligono(c, new float[]{-8, -8, 0, Lance.L + 8, -8, 0, Lance.L + 8, -26, 15, -8, -26, 15}, p);
        poligono(c, new float[]{-8, Lance.W + 8, 0, Lance.L + 8, Lance.W + 8, 0, Lance.L + 8, Lance.W + 26, 15, -8, Lance.W + 26, 15}, p);
        p.setColor(corArq);
        poligono(c, new float[]{-8, -14, 0, -8, Lance.W + 14, 0, -26, Lance.W + 14, 15, -26, -14, 15}, p);
        // cobertura
        p.setColor(0xFF0E1622);
        poligono(c, new float[]{Lance.L + 24, -16, 15, Lance.L + 24, Lance.W + 16, 15, Lance.L + 28, Lance.W + 16, 19, Lance.L + 28, -16, 19}, p);
        // torcida
        for (int i = 0; i < torcida.length / 4; i++) {
            float[] q = cam(torcida[i * 4], torcida[i * 4 + 1], torcida[i * 4 + 2]);
            if (q[2] < 1) continue;
            float x = px(q), y = py(q);
            if (x < -10 || x > W + 10 || y < -10 || y > H + 10) continue;
            float r = Math.max(1.2f, 0.32f * foco / q[2]);
            p.setColor((int) torcida[i * 4 + 3]);
            c.drawCircle(x, y, r, p);
        }
        // placas de publicidade
        p.setColor(0xFF15803D);
        poligono(c, new float[]{Lance.L + 4, -2, 0, Lance.L + 4, Lance.W + 2, 0, Lance.L + 4, Lance.W + 2, 0.9f, Lance.L + 4, -2, 0.9f}, p);
        p.setColor(0xFF1D4ED8);
        poligono(c, new float[]{-2, -4, 0, Lance.L + 2, -4, 0, Lance.L + 2, -4, 0.9f, -2, -4, 0.9f}, p);
        poligono(c, new float[]{-2, Lance.W + 4, 0, Lance.L + 2, Lance.W + 4, 0, Lance.L + 2, Lance.W + 4, 0.9f, -2, Lance.W + 4, 0.9f}, p);
    }

    private void gramado(Canvas c) {
        p.setColor(0xFF1F6B2C);
        poligono(c, new float[]{-8, -8, 0, Lance.L + 8, -8, 0, Lance.L + 8, Lance.W + 8, 0, -8, Lance.W + 8, 0}, p);
        int n = 18;
        for (int i = 0; i < n; i++) {
            float x0 = i * Lance.L / n, x1 = (i + 1) * Lance.L / n;
            p.setColor(i % 2 == 0 ? 0xFF2F8F3E : 0xFF35A046);
            poligono(c, new float[]{x0, 0, 0, x1, 0, 0, x1, Lance.W, 0, x0, Lance.W, 0}, p);
        }
        int b = 0xEEFFFFFF;
        float z = 0.01f;
        linha(c, 0, 0, z, Lance.L, 0, z, .12f, b);
        linha(c, 0, Lance.W, z, Lance.L, Lance.W, z, .12f, b);
        linha(c, 0, 0, z, 0, Lance.W, z, .12f, b);
        linha(c, Lance.L, 0, z, Lance.L, Lance.W, z, .12f, b);
        linha(c, Lance.L / 2, 0, z, Lance.L / 2, Lance.W, z, .12f, b);
        circuloChao(c, Lance.L / 2, 34, 9.15f, 0, (float) (Math.PI * 2), b);
        for (int lado = 0; lado < 2; lado++) {
            float g = lado == 0 ? 0 : Lance.L, sg = lado == 0 ? 1 : -1;
            float a1 = g + sg * 16.5f, a2 = g + sg * 5.5f;
            linha(c, g, 34 - 20.16f, z, a1, 34 - 20.16f, z, .12f, b);
            linha(c, g, 34 + 20.16f, z, a1, 34 + 20.16f, z, .12f, b);
            linha(c, a1, 34 - 20.16f, z, a1, 34 + 20.16f, z, .12f, b);
            linha(c, g, 34 - 9.16f, z, a2, 34 - 9.16f, z, .12f, b);
            linha(c, g, 34 + 9.16f, z, a2, 34 + 9.16f, z, .12f, b);
            linha(c, a2, 34 - 9.16f, z, a2, 34 + 9.16f, z, .12f, b);
            float ps = g + sg * 11;
            circuloChao(c, ps, 34, 0.15f, 0, (float) (Math.PI * 2), b);
            if (lado == 1) circuloChao(c, ps, 34, 9.15f, (float) Math.toRadians(127), (float) Math.toRadians(233), b);
            else circuloChao(c, ps, 34, 9.15f, (float) Math.toRadians(-53), (float) Math.toRadians(53), b);
        }
    }

    private void gol(Canvas c, float gx, int sg) {
        float fundo = gx + sg * 2.2f;
        int rede = 0x88FFFFFF;
        // rede: fundo, laterais e teto
        p.setColor(0x22FFFFFF);
        poligono(c, new float[]{fundo, Lance.GOL_Y1, 0, fundo, Lance.GOL_Y2, 0, fundo, Lance.GOL_Y2, 2.2f, fundo, Lance.GOL_Y1, 2.2f}, p);
        for (float y = Lance.GOL_Y1; y <= Lance.GOL_Y2 + 0.01f; y += 0.5f) {
            linha(c, fundo, y, 0, fundo, y, 2.2f, 0.02f, rede);
            linha(c, gx, y, Lance.TRAVE, fundo, y, 2.2f, 0.02f, rede);
        }
        for (float z = 0.4f; z <= 2.2f; z += 0.45f) {
            linha(c, fundo, Lance.GOL_Y1, z, fundo, Lance.GOL_Y2, z, 0.02f, rede);
            linha(c, gx, Lance.GOL_Y1, z * Lance.TRAVE / 2.2f, fundo, Lance.GOL_Y1, z, 0.02f, rede);
            linha(c, gx, Lance.GOL_Y2, z * Lance.TRAVE / 2.2f, fundo, Lance.GOL_Y2, z, 0.02f, rede);
        }
        // traves
        linha(c, gx, Lance.GOL_Y1, 0, gx, Lance.GOL_Y1, Lance.TRAVE, 0.13f, 0xFFF8FAFC);
        linha(c, gx, Lance.GOL_Y2, 0, gx, Lance.GOL_Y2, Lance.TRAVE, 0.13f, 0xFFF8FAFC);
        linha(c, gx, Lance.GOL_Y1, Lance.TRAVE, gx, Lance.GOL_Y2, Lance.TRAVE, 0.13f, 0xFFF8FAFC);
    }

    private void sombra(Canvas c, float x, float y, float r) {
        float[] q = cam(x, y, 0.01f);
        if (q[2] < PERTO) return;
        float rr = r * foco / q[2];
        p.setColor(0x55000000);
        c.drawOval(px(q) - rr * 1.2f, py(q) - rr * 0.45f, px(q) + rr * 1.2f, py(q) + rr * 0.45f, p);
    }

    static int[] PELES = {0xFFF1C27D, 0xFFC68642, 0xFF8D5524, 0xFFE0AC69, 0xFF5C3A21};

    private void jogador(Canvas c, Lance.Ator a) {
        float[] pe = cam(a.x, a.y, 0), cab = cam(a.x, a.y, 1.75f);
        if (pe[2] < 1.0f) return;
        float fx = px(pe), fy = py(pe), hx = px(cab), hy = py(cab);
        float esc = foco / pe[2]; // px por metro
        if (fx < -esc * 2 || fx > W + esc * 2) return;
        Clube cl = clubes[a.time];
        int camisa = cl.cor1, calcao = cl.cor2;
        if (a.time == 1 && clubes[0] != null && parecido(clubes[0].cor1, cl.cor1)) {
            camisa = cl.cor2;
            calcao = cl.cor1;
        }
        if (a.goleiro) {
            camisa = a.time == 0 ? 0xFFF97316 : 0xFFA3E635;
            calcao = 0xFF111827;
        }
        int pele = PELES[Math.abs(a.j.id) % PELES.length];
        // ponto ao longo do corpo: k = altura em metros
        float corre = (float) Math.hypot(a.vx, a.vy) > 0.4f ? 1 : 0;
        float bal = (float) Math.sin(a.fase) * corre;
        float larg = esc;
        // pernas
        float quadril = 0.92f;
        float[] qd = ponto(fx, fy, hx, hy, quadril);
        s.setStrokeCap(Paint.Cap.ROUND);
        for (int lado = -1; lado <= 1; lado += 2) {
            float pf = lado * bal * 0.22f * larg;
            float bxp = fx + lado * 0.11f * larg + pf * 0.3f, byp = fy - Math.max(0, lado * bal) * 0.08f * larg;
            s.setStrokeWidth(0.15f * larg);
            s.setColor(pele);
            c.drawLine(qd[0] + lado * 0.1f * larg, qd[1], bxp, byp - 0.12f * larg, s);
            s.setColor(camisa); // meião
            s.setStrokeWidth(0.16f * larg);
            float[] joelho = {bxp + (qd[0] + lado * 0.1f * larg - bxp) * 0.45f, byp - 0.12f * larg + (qd[1] - byp + 0.12f * larg) * 0.45f};
            c.drawLine(joelho[0], joelho[1], bxp, byp - 0.1f * larg, s);
            s.setColor(0xFF111111); // chuteira
            s.setStrokeWidth(0.13f * larg);
            c.drawLine(bxp - 0.05f * larg, byp - 0.04f * larg, bxp + 0.07f * larg, byp - 0.04f * larg, s);
        }
        // calção
        float[] c1 = ponto(fx, fy, hx, hy, 1.02f), c0 = ponto(fx, fy, hx, hy, 0.78f);
        p.setColor(calcao);
        c.drawRoundRect(c1[0] - 0.22f * larg, c1[1], c1[0] + 0.22f * larg, c0[1], 0.06f * larg, 0.06f * larg, p);
        // tronco
        float[] t1 = ponto(fx, fy, hx, hy, 1.5f), t0 = ponto(fx, fy, hx, hy, 1.0f);
        p.setShader(new LinearGradient(t1[0] - 0.25f * larg, 0, t1[0] + 0.25f * larg, 0, new int[]{escurecer(camisa, .75f), camisa, escurecer(camisa, .85f)}, null, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(t1[0] - 0.25f * larg, t1[1]);
        path.lineTo(t1[0] + 0.25f * larg, t1[1]);
        path.lineTo(t0[0] + 0.21f * larg, t0[1]);
        path.lineTo(t0[0] - 0.21f * larg, t0[1]);
        path.close();
        c.drawPath(path, p);
        p.setShader(null);
        // braços
        for (int lado = -1; lado <= 1; lado += 2) {
            float sw = -lado * bal * 0.15f * larg;
            s.setColor(camisa);
            s.setStrokeWidth(0.12f * larg);
            float ox = t1[0] + lado * 0.27f * larg, oy = t1[1] + 0.05f * larg;
            float mx = ox + lado * 0.06f * larg + sw * 0.4f, my = oy + 0.22f * larg;
            c.drawLine(ox, oy, mx, my, s);
            s.setColor(a.goleiro ? 0xFFF8FAFC : pele);
            s.setStrokeWidth(0.1f * larg);
            c.drawLine(mx, my, mx + sw * 0.4f, my + 0.22f * larg, s);
        }
        // número nas costas (meu time é visto de costas)
        if (a.time == 0 && larg > 14) {
            t.setColor(calcao == camisa ? 0xFFFFFFFF : calcao);
            t.setTextSize(0.28f * larg);
            c.drawText(String.valueOf(a.numero), (t1[0] + t0[0]) / 2, (t1[1] + t0[1]) / 2 + 0.1f * larg, t);
        }
        // cabeça
        float[] hc = ponto(fx, fy, hx, hy, 1.66f);
        p.setColor(pele);
        c.drawCircle(hc[0], hc[1], 0.12f * larg, p);
        p.setColor(a.time == 0 ? 0xFF1F140D : 0xFF2B1B10);
        c.drawArc(hc[0] - 0.125f * larg, hc[1] - 0.13f * larg, hc[0] + 0.125f * larg, hc[1] + (a.time == 0 ? 0.1f : 0.02f) * larg, 180, 180, true, p);
        // nome de quem está com a bola
        if (lance != null && lance.portador == a) {
            t.setTextSize(Math.max(12 * d, 0.3f * larg));
            t.setColor(0xFFFFFFFF);
            t.setShadowLayer(5, 0, 1, 0xFF000000);
            c.drawText(a.j.nome, hx, hy - 0.35f * larg, t);
            t.clearShadowLayer();
        }
    }

    private float[] ponto(float fx, float fy, float hx, float hy, float alt) {
        float k = alt / 1.75f;
        return new float[]{fx + (hx - fx) * k, fy + (hy - fy) * k};
    }

    static int escurecer(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    static boolean parecido(int a, int b) {
        int dr = Color.red(a) - Color.red(b), dg = Color.green(a) - Color.green(b), db = Color.blue(a) - Color.blue(b);
        return dr * dr + dg * dg + db * db < 9000;
    }

    private void bola(Canvas c, float x, float y, float z) {
        float[] q = cam(x, y, z);
        if (q[2] < PERTO) return;
        float r = Math.max(3 * d, 0.16f * foco / q[2]);
        float bx = px(q), by = py(q);
        p.setShader(new RadialGradient(bx - r * .35f, by - r * .35f, r * 1.4f, 0xFFFFFFFF, 0xFFB8C0C8, Shader.TileMode.CLAMP));
        c.drawCircle(bx, by, r, p);
        p.setShader(null);
        p.setColor(0xFF1F2937);
        float rot = lance != null ? (lance.bx + lance.by) * 1.3f : 0;
        for (int i = 0; i < 3; i++) {
            double a = rot + i * 2.1;
            c.drawCircle(bx + (float) Math.cos(a) * r * .45f, by + (float) Math.sin(a) * r * .45f, r * .22f, p);
        }
    }

    private void anelPortador(Canvas c) {
        float[] q = cam(lance.bx, lance.by, 0.02f);
        if (q[2] < PERTO) return;
        float r = 0.9f * foco / q[2];
        s.setStrokeWidth(3 * d);
        s.setColor(0xFFFACC15);
        c.drawOval(px(q) - r, py(q) - r * .4f, px(q) + r, py(q) + r * .4f, s);
        // pressão do defensor
        if (lance.pressao > 0.05f) {
            s.setColor(0xFFEF4444);
            s.setStrokeWidth(5 * d);
            c.drawArc(px(q) - r * 1.3f, py(q) - r * .55f, px(q) + r * 1.3f, py(q) + r * .55f, -90, 360 * Math.min(1, lance.pressao), false, s);
        }
    }

    private void guia(Canvas c) {
        int n = previa.length / 3;
        for (int i = 0; i < n; i += 1) {
            float[] q = cam(previa[i * 3], previa[i * 3 + 1], previa[i * 3 + 2]);
            if (q[2] < PERTO) continue;
            float a = 1 - i / (float) n;
            p.setColor(Color.argb((int) (200 * a), 255, 235, 59));
            c.drawCircle(px(q), py(q), Math.max(2 * d, 0.09f * foco / q[2]), p);
        }
        float[] q = cam(alvoX, alvoY, alvoGol ? alvoZ : 0.02f);
        if (q[2] > PERTO) {
            s.setColor(alvoGol ? 0xFFEF4444 : 0xFFFACC15);
            s.setStrokeWidth(3 * d);
            float r = (alvoGol ? 0.35f : 0.8f) * foco / q[2];
            if (alvoGol) {
                c.drawCircle(px(q), py(q), r, s);
                c.drawLine(px(q) - r * 1.4f, py(q), px(q) + r * 1.4f, py(q), s);
                c.drawLine(px(q), py(q) - r * 1.4f, px(q), py(q) + r * 1.4f, s);
            } else c.drawOval(px(q) - r, py(q) - r * .4f, px(q) + r, py(q) + r * .4f, s);
        }
        // traço do dedo
        s.setColor(0x88FFFFFF);
        s.setStrokeWidth(4 * d);
        for (int i = 1; i < trilha.size(); i++) c.drawLine(trilha.get(i - 1)[0], trilha.get(i - 1)[1], trilha.get(i)[0], trilha.get(i)[1], s);
    }

    private void hud(Canvas c) {
        if (lance != null && lance.estado == Lance.ESPERANDO && !arrastando) {
            t.setTextSize(15 * d);
            t.setColor(0xFFFFFFFF);
            t.setShadowLayer(6, 0, 1, 0xFF000000);
            String dica = lance.tipo == Lance.PENALTI ? "Arraste até o canto do gol para bater o pênalti"
                    : lance.tipo == Lance.FALTA ? "Falta! Arraste até o gol fazendo um arco para dar efeito"
                    : "Arraste da bola até um companheiro (passe) ou até o gol (chute)";
            c.drawText(dica, W / 2f, H - 22 * d, t);
            t.setTextSize(12 * d);
            c.drawText("Gesto rápido = mais força / passe pelo alto · Arco no gesto = curva", W / 2f, H - 6 * d, t);
            t.clearShadowLayer();
        }
        if (mensagemT > 0 && mensagem != null) {
            boolean gol = mensagem.startsWith("GOO");
            float a = Math.min(1, mensagemT * 2);
            t.setTextSize((gol ? 64 : 36) * d);
            t.setColor(Color.argb((int) (255 * a), gol ? 250 : 255, gol ? 204 : 255, gol ? 21 : 255));
            t.setShadowLayer(12, 0, 3, Color.argb((int) (220 * a), 0, 0, 0));
            c.drawText(mensagem, W / 2f, H * 0.42f, t);
            if (sub != null && !sub.isEmpty()) {
                t.setTextSize(18 * d);
                t.setColor(Color.argb((int) (255 * a), 255, 255, 255));
                c.drawText(sub, W / 2f, H * 0.42f + 34 * d, t);
            }
            t.clearShadowLayer();
        }
    }

    // ================================================================== gesto

    /** onde o dedo aponta: no gol (y, z na linha do gol) ou no gramado */
    private boolean mirar(float sx, float sy) {
        float a = (sx - W / 2f) / foco, b = (H / 2f - sy) / foco;
        float dx = fX + rX * a + uX * b, dy = fY + rY * a + uY * b, dz = fZ + rZ * a + uZ * b;
        // plano do gol (x = L)
        if (dx > 0.01f) {
            float k = (Lance.L - cx) / dx;
            float gy = cy + dy * k, gz = cz + dz * k;
            if (gy > Lance.GOL_Y1 - 3.5f && gy < Lance.GOL_Y2 + 3.5f && gz > -0.3f && gz < 4.5f && (dz >= 0 || gz >= -0.3f)) {
                alvoGol = true;
                alvoX = Lance.L;
                alvoY = gy;
                alvoZ = Math.max(0.15f, gz);
                return true;
            }
        }
        if (dz < -0.005f) {
            float k = -cz / dz;
            alvoGol = false;
            alvoX = cx + dx * k;
            alvoY = cy + dy * k;
            alvoZ = 0;
            if (alvoX > Lance.L - 0.5f) alvoX = Lance.L - 0.5f;
            return true;
        }
        // acima do horizonte: lançamento longo na direção
        float n = (float) Math.hypot(dx, dy);
        alvoGol = false;
        alvoX = lance.bx + dx / n * 40;
        alvoY = lance.by + dy / n * 40;
        return true;
    }

    private float[] parametros(long agora) {
        float[] a = trilha.get(0), b = trilha.get(trilha.size() - 1);
        float lx = b[0] - a[0], ly = b[1] - a[1], len = (float) Math.hypot(lx, ly);
        float dur = Math.max(60, agora - inicioGesto);
        float vel = len / dur; // px por ms
        float forca = Math.max(0.15f, Math.min(1f, vel / (2.4f * d)));
        // curva: maior afastamento da trilha em relação à reta (lado)
        float maior = 0;
        for (float[] q : trilha) {
            float cr = ((q[0] - a[0]) * ly - (q[1] - a[1]) * lx) / Math.max(1, len);
            if (Math.abs(cr) > Math.abs(maior)) maior = cr;
        }
        float curva = Math.max(-1, Math.min(1, maior / Math.max(40 * d, len) * 3.2f));
        boolean alto = vel > 2.0f * d;
        return new float[]{forca, curva, alto ? 1 : 0};
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (lance == null || lance.estado != Lance.ESPERANDO) return true;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                arrastando = true;
                trilha.clear();
                trilha.add(new float[]{x, y});
                inicioGesto = System.currentTimeMillis();
                previa = null;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!arrastando) break;
                trilha.add(new float[]{x, y});
                if (trilha.size() > 2 && mirar(x, y)) {
                    float[] pr = parametros(System.currentTimeMillis());
                    previa = lance.previa(alvoX, alvoY, alvoZ, alvoGol, Math.max(0.55f, pr[0]), pr[1], !alvoGol && pr[2] > 0);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!arrastando) break;
                arrastando = false;
                trilha.add(new float[]{x, y});
                float[] a = trilha.get(0);
                if (Math.hypot(x - a[0], y - a[1]) < 30 * d) {
                    previa = null;
                    break; // toque curto: ignora
                }
                mirar(x, y);
                float[] pr = parametros(System.currentTimeMillis());
                lance.chutar(alvoX, alvoY, alvoZ, alvoGol, pr[0], pr[1], !alvoGol && pr[2] > 0);
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                previa = null;
                break;
            case MotionEvent.ACTION_CANCEL:
                arrastando = false;
                previa = null;
                break;
        }
        return true;
    }
}
