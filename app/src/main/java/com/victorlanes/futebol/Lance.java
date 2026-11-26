package com.victorlanes.futebol;

import com.victorlanes.futebol.Modelo.Clube;
import com.victorlanes.futebol.Modelo.Jogador;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Um lance de ataque no estilo New Star Soccer: o jogo congela com a bola no pé de um jogador do seu time
 * e você arrasta o dedo a partir da bola para passar ou chutar (com força, altura e curva).
 * Coordenadas em metros: x = comprimento (o gol adversário fica em x = 105), y = largura (0..68), z = altura.
 */
public final class Lance {

    public static final float L = 105, W = 68, G = 9.8f;
    public static final float GOL_Y1 = 34 - 3.66f, GOL_Y2 = 34 + 3.66f, TRAVE = 2.44f;
    public static final int ESPERANDO = 0, VOANDO = 1, FIM = 2;
    public static final int ATAQUE = 0, CONTRA = 1, FALTA = 2, PENALTI = 3;

    public static final class Ator {
        public float x, y, vx, vy, ax, ay; // posição, velocidade, alvo
        public int time; // 0 = meu, 1 = adversário
        public boolean goleiro, barreira, tentou;
        public Jogador j;
        public float fase; // animação da corrida
        public int vel, def, gk, fin, pas;
        public int numero;

        float velMax() {
            return 4.5f + vel / 100f * 3.5f;
        }
    }

    public final List<Ator> atores = new ArrayList<>();
    public float bx, by, bz, bvx, bvy, bvz, spin;
    public Ator portador; // quem está com a bola
    public int estado = ESPERANDO, tipo, toques, maxToques = 5;
    public String resultado = "", detalhe = "";
    public boolean gol;
    public Jogador autor;
    public float pressao; // 0..1: quando chega a 1 o defensor rouba a bola
    public float tempoVoo;
    public boolean chuteNoGol;
    private boolean decidiuGoleiro;
    private Ator ultimoChutador;
    private final Random rnd;
    private final float dif;
    public final Clube meu, adv;

    public Lance(Random rnd, Clube meu, Clube adv, int tipo, int dificuldade) {
        this.rnd = rnd;
        this.meu = meu;
        this.adv = adv;
        this.tipo = tipo;
        this.dif = dificuldade == 0 ? 0.8f : dificuldade == 2 ? 1.2f : 1f;
        montar();
    }

    // ================================================================== montagem do lance

    private List<Jogador> meusPor(int... funcoes) {
        List<Jogador> l = new ArrayList<>();
        float[][] v = meu.vagas();
        List<Jogador> t = meu.titulares();
        for (int f : funcoes) for (int i = 0; i < 11; i++) if ((int) v[i][2] == f && !l.contains(t.get(i))) l.add(t.get(i));
        for (Jogador j : t) if (!l.contains(j) && j.pos != 0) l.add(j);
        return l;
    }

    private List<Jogador> delesPor(int... funcoes) {
        List<Jogador> l = new ArrayList<>();
        float[][] v = adv.vagas();
        List<Jogador> t = adv.titulares();
        for (int f : funcoes) for (int i = 0; i < 11; i++) if ((int) v[i][2] == f && !l.contains(t.get(i))) l.add(t.get(i));
        for (Jogador j : t) if (!l.contains(j) && j.pos != 0) l.add(j);
        return l;
    }

    private Ator ator(int time, Jogador j, float x, float y) {
        Ator a = new Ator();
        a.time = time;
        a.j = j;
        a.x = a.ax = x;
        a.y = a.ay = y;
        a.numero = j.numero;
        float bonus = time == 1 ? (dif - 1) * 10 : 0;
        a.vel = cl(j.a[0] + bonus);
        a.fin = cl(j.a[1]);
        a.pas = cl(j.a[2]);
        a.def = cl(j.a[4] + bonus);
        a.gk = cl(j.a[5] + bonus);
        a.fase = rnd.nextFloat() * 6;
        atores.add(a);
        return a;
    }

    static int cl(float v) {
        return Math.max(10, Math.min(99, Math.round(v)));
    }

    private float r(float a, float b) {
        return a + rnd.nextFloat() * (b - a);
    }

    private void montar() {
        List<Jogador> ataque = meusPor(5, 4, 2, 3, 1);
        List<Jogador> defesa = delesPor(1, 2, 3, 4, 5);
        Jogador goleiro = adv.titulares().get(0);
        float forcaDif = adv.forca() - meu.forca();
        int extra = forcaDif > 10 ? 2 : forcaDif > 4 ? 1 : forcaDif < -8 ? -1 : 0;
        Ator g = ator(1, goleiro, L - 1.2f, 34);
        g.goleiro = true;

        if (tipo == PENALTI) {
            portador = ator(0, melhorFinalizador(), L - 11, 34);
            bx = portador.x + 0.3f;
            by = 34;
            g.x = L - 0.3f;
            maxToques = 1;
            for (int i = 0; i < 4; i++) ator(1, defesa.get(i), L - 20 - r(0, 2), 20 + i * 9 + r(-1, 1));
            for (int i = 0; i < 3; i++) ator(0, ataque.get(i + 1), L - 20 - r(0, 2), 24 + i * 9 + r(-1, 1));
            return;
        }
        if (tipo == FALTA) {
            float d = r(19, 27), py = r(20, 48);
            portador = ator(0, melhorFinalizador(), L - d - 0.6f, py);
            bx = L - d;
            by = py;
            maxToques = 2;
            // barreira a 9,15 m na linha da bola até o centro do gol
            float dx = L - bx, dy = 34 - by, n = (float) Math.hypot(dx, dy);
            float ux = dx / n, uy = dy / n;
            float wx = bx + ux * 9.15f, wy = by + uy * 9.15f;
            int nb = 3 + (d < 23 ? 1 : 0);
            for (int i = 0; i < nb; i++) {
                float off = (i - (nb - 1) / 2f) * 0.62f - (by < 34 ? 0.8f : -0.8f);
                Ator a = ator(1, defesa.get(i), wx - uy * off, wy + ux * off);
                a.barreira = true;
            }
            g.y = 34 + (by < 34 ? 1.2f : -1.2f);
            for (int i = 0; i < 3; i++) ator(1, defesa.get(nb + i), L - r(6, 12), r(26, 42));
            for (int i = 0; i < 2; i++) ator(0, ataque.get(i + 1), L - r(7, 13), r(25, 43));
            return;
        }
        if (tipo == CONTRA) {
            float px = L - r(38, 50), py = r(22, 46);
            portador = ator(0, ataque.get(2), px, py);
            bx = px + 0.6f;
            by = py;
            int nd = Math.max(1, 2 + extra);
            for (int i = 0; i < nd; i++) ator(1, defesa.get(i), L - r(18, 26), 22 + i * 24f / Math.max(1, nd - 1) + r(-2, 2));
            ator(1, defesa.get(nd), px - r(3, 6), py + r(-4, 4)); // vem perseguindo
            ator(0, ataque.get(0), px + r(8, 14), r(14, 24));
            ator(0, ataque.get(1), px + r(8, 14), r(44, 54));
            return;
        }
        // ataque posicional
        float px = L - r(27, 38), py = r(16, 52);
        portador = ator(0, ataque.get(3), px, py);
        bx = px + 0.6f;
        by = py;
        float linha = L - r(13, 18);
        for (int i = 0; i < 4; i++) ator(1, defesa.get(i), linha + r(-1, 1), 16 + i * 12 + r(-2, 2));
        int meio = 1 + Math.max(0, extra);
        for (int i = 0; i < meio; i++) ator(1, defesa.get(4 + i), px + r(5, 9), py + (i == 0 ? r(-4, 4) : (py > 34 ? -9 : 9)));
        ator(0, ataque.get(0), linha - r(1, 3), r(26, 33));
        ator(0, ataque.get(1), linha - r(1, 3), r(36, 44));
        ator(0, ataque.get(2), linha - r(4, 8), py > 34 ? r(5, 11) : r(57, 63));
        ator(0, ataque.get(4), px - r(5, 8), py + (py > 34 ? -8 : 8));
    }

    private Jogador melhorFinalizador() {
        Jogador m = null;
        for (Jogador j : meu.titulares()) if (j.pos != 0 && (m == null || j.a[1] > m.a[1])) m = j;
        return m;
    }

    // ================================================================== chute / passe

    /** posição futura da bola sem erro (para a linha-guia) */
    public float[] previa(float tx, float ty, float tz, boolean noGol, float forca, float curva, boolean alto) {
        float[] v = velocidadeInicial(tx, ty, tz, noGol, forca, curva, alto, false);
        float x = bx, y = by, z = Math.max(0.11f, bz), vx = v[0], vy = v[1], vz = v[2], sp = v[3];
        int n = 36;
        float[] pts = new float[n * 3];
        float dt = 0.05f;
        for (int i = 0; i < n; i++) {
            pts[i * 3] = x;
            pts[i * 3 + 1] = y;
            pts[i * 3 + 2] = z;
            for (int k = 0; k < 2; k++) {
                float[] s = fisica(x, y, z, vx, vy, vz, sp, dt / 2);
                x = s[0];
                y = s[1];
                z = s[2];
                vx = s[3];
                vy = s[4];
                vz = s[5];
            }
            if (x > L + 2 || Math.hypot(vx, vy) < 0.3f && z <= 0.12f) {
                float[] r = new float[(i + 1) * 3];
                System.arraycopy(pts, 0, r, 0, r.length);
                return r;
            }
        }
        return pts;
    }

    /** devolve vx, vy, vz e spin */
    private float[] velocidadeInicial(float tx, float ty, float tz, boolean noGol, float forca, float curva, boolean alto, boolean comErro) {
        Ator p = portador;
        float dx = tx - bx, dy = ty - by, d = (float) Math.max(0.5, Math.hypot(dx, dy));
        float ux = dx / d, uy = dy / d;
        float vh, vz, t;
        float spin = curva * 11f;
        if (noGol) {
            vh = 15 + forca * 17 + p.fin / 100f * 4;
            t = d / vh;
            vz = (tz - 0.11f + 0.5f * G * t * t) / t;
        } else if (alto) {
            t = 0.7f + d / 24f;
            vh = d / t;
            vz = 0.5f * G * t;
        } else {
            vh = (float) Math.min(30, Math.sqrt(3.5 * 3.5 + 2 * 3.2 * d));
            t = d / Math.max(1, vh * 0.75f);
            vz = 0.6f;
            spin *= 0.6f;
        }
        // compensa a curva para a bola terminar perto do alvo
        float px = -uy, py = ux; // perpendicular
        float desvio = -0.5f * spin * t * t;
        float ax = tx + px * desvio, ay = ty + py * desvio;
        float ndx = ax - bx, ndy = ay - by, nd = (float) Math.max(0.5, Math.hypot(ndx, ndy));
        ux = ndx / nd;
        uy = ndy / nd;
        if (comErro) {
            float hab = noGol ? p.fin : p.pas;
            float sig = (0.01f + (1 - hab / 100f) * 0.035f + (noGol ? forca * 0.025f : 0)) * (tipo == PENALTI ? 0.7f : 1);
            float e = (float) (rnd.nextGaussian() * sig);
            float c = (float) Math.cos(e), s = (float) Math.sin(e);
            float nx = ux * c - uy * s, ny = ux * s + uy * c;
            ux = nx;
            uy = ny;
            vz *= 1 + (float) rnd.nextGaussian() * sig * 1.6f;
            vh *= 1 + (float) rnd.nextGaussian() * 0.03f;
        }
        return new float[]{ux * vh, uy * vh, vz, spin};
    }

    public void chutar(float tx, float ty, float tz, boolean noGol, float forca, float curva, boolean alto) {
        if (estado != ESPERANDO) return;
        float[] v = velocidadeInicial(tx, ty, tz, noGol, forca, curva, alto, true);
        bvx = v[0];
        bvy = v[1];
        bvz = v[2];
        spin = v[3];
        bz = Math.max(0.11f, bz);
        estado = VOANDO;
        tempoVoo = 0;
        chuteNoGol = noGol;
        decidiuGoleiro = false;
        ultimoChutador = portador;
        toques++;
        for (Ator a : atores) a.tentou = false;
        // companheiros mais perto do alvo correm para receber
        if (!noGol) {
            Ator rec = null;
            float md = 1e9f;
            for (Ator a : atores)
                if (a.time == 0 && a != portador) {
                    float dd = (float) Math.hypot(a.x - tx, a.y - ty);
                    if (dd < md) {
                        md = dd;
                        rec = a;
                    }
                }
            if (rec != null && md < 12) {
                rec.ax = tx;
                rec.ay = ty;
            }
        }
        portador = null;
    }

    // ================================================================== física e IA

    private float[] fisica(float x, float y, float z, float vx, float vy, float vz, float sp, float dt) {
        float h = (float) Math.hypot(vx, vy);
        if (h > 0.2f) { // efeito (curva) perpendicular ao movimento
            float f = z > 0.15f ? 1 : 0.35f;
            vx += -vy / h * sp * f * dt;
            vy += vx / h * sp * f * dt;
        }
        if (z > 0.12f || vz > 0) {
            vz -= G * dt;
            float ar = (float) Math.exp(-0.06f * dt);
            vx *= ar;
            vy *= ar;
        } else {
            float nh = Math.max(0, h - 3.2f * dt);
            if (h > 0) {
                vx = vx / h * nh;
                vy = vy / h * nh;
            }
        }
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;
        if (z < 0.11f) {
            z = 0.11f;
            if (vz < -1.2f) {
                vz = -vz * 0.42f;
                vx *= 0.82f;
                vy *= 0.82f;
            } else vz = 0;
        }
        return new float[]{x, y, z, vx, vy, vz};
    }

    public void passo(float dt) {
        for (Ator a : atores) {
            float sp = (float) Math.hypot(a.vx, a.vy);
            a.fase += sp * dt * 2.2f;
        }
        if (estado == FIM) {
            moverAtores(dt, false);
            if (portador == null) {
                float[] s = fisica(bx, by, bz, bvx, bvy, bvz, spin * 0.5f, dt);
                bx = s[0];
                by = s[1];
                bz = s[2];
                bvx = s[3] * 0.98f;
                bvy = s[4] * 0.98f;
                bvz = s[5];
            }
            return;
        }
        if (estado == ESPERANDO) {
            // o defensor mais perto vai apertando
            if (tipo != FALTA && tipo != PENALTI) {
                Ator d = maisPerto(1, bx, by, true);
                if (d != null) {
                    d.ax = bx;
                    d.ay = by;
                    float dist = (float) Math.hypot(d.x - bx, d.y - by);
                    pressao = Math.max(pressao, 1 - (dist - 1) / 9f);
                    if (dist < 1.1f) {
                        terminar("Desarmado!", d.j.nome + " roubou a bola.", false);
                        return;
                    }
                }
            }
            moverAtores(dt, true);
            return;
        }
        // VOANDO
        tempoVoo += dt;
        float[] s = fisica(bx, by, bz, bvx, bvy, bvz, spin, dt);
        bx = s[0];
        by = s[1];
        bz = s[2];
        bvx = s[3];
        bvy = s[4];
        bvz = s[5];
        float vel = (float) Math.hypot(bvx, bvy);
        moverAtores(dt, false);
        goleiro(dt, vel);
        if (estado == FIM) return;

        // linha de fundo / gol
        if (bx >= L) {
            if (by > GOL_Y1 && by < GOL_Y2 && bz < TRAVE) {
                gol = true;
                autor = ultimoChutador != null ? ultimoChutador.j : null;
                terminar("GOOOL!", autor != null ? autor.nome : "", true);
            } else if ((Math.abs(by - GOL_Y1) < 0.25f || Math.abs(by - GOL_Y2) < 0.25f) && bz < TRAVE + 0.1f || (by > GOL_Y1 && by < GOL_Y2 && Math.abs(bz - TRAVE) < 0.18f)) {
                bvx = -bvx * 0.5f;
                terminar("Na trave!", "Que azar!", false);
            } else terminar(chuteNoGol ? "Para fora!" : "Saiu pela linha de fundo", "", false);
            return;
        }
        if (by < 0 || by > W || bx < 0) {
            terminar("A bola saiu", "", false);
            return;
        }
        // interceptação / bloqueio
        for (Ator a : atores) {
            if (a.time != 1 || a.goleiro || a.tentou) continue;
            float d = (float) Math.hypot(a.x - bx, a.y - by);
            float alcance = a.barreira ? 0.55f : 0.95f;
            float altura = a.barreira ? 2.15f : 2.05f;
            if (d < alcance && bz < altura) {
                a.tentou = true;
                float p = a.barreira ? 0.9f : 0.1f + a.def / 250f - vel / 100f + (bz < 0.5f ? 0.08f : 0);
                p *= dif;
                if (rnd.nextFloat() < Math.max(0.06f, Math.min(0.92f, p))) {
                    bvx *= -0.25f;
                    bvy = bvy * 0.3f + (rnd.nextFloat() - .5f) * 6;
                    bvz = 2;
                    terminar(a.barreira ? "Na barreira!" : chuteNoGol ? "Bloqueado!" : "Interceptado!", a.j.nome + " cortou.", false);
                    return;
                }
            }
        }
        // recepção de um companheiro
        if (tempoVoo > 0.25f && !(chuteNoGol && vel > 12)) {
            for (Ator a : atores) {
                if (a.time != 0 || a == ultimoChutador && tempoVoo < 0.6f) continue;
                float d = (float) Math.hypot(a.x - bx, a.y - by);
                if (d < 1.25f && bz < 1.9f) {
                    receber(a);
                    return;
                }
            }
        }
        // bola parou: quem chega primeiro?
        if (vel < 1.2f && bz <= 0.12f) {
            Ator meuP = maisPerto(0, bx, by, true), dele = maisPerto(1, bx, by, false);
            float dm = meuP == null ? 999 : (float) Math.hypot(meuP.x - bx, meuP.y - by) / meuP.velMax();
            float dd = dele == null ? 999 : (float) Math.hypot(dele.x - bx, dele.y - by) / dele.velMax();
            if (dm < dd && meuP != null) receber(meuP);
            else terminar("Perdeu a bola", "A defesa ficou com ela.", false);
        }
    }

    private void receber(Ator a) {
        if (toques >= maxToques) {
            terminar("A defesa se recompôs", "Muitos toques, a jogada esfriou.", false);
            return;
        }
        portador = a;
        a.vx = a.vy = 0;
        a.ax = a.x;
        a.ay = a.y;
        bx = a.x + 0.55f;
        by = a.y;
        bz = 0.11f;
        bvx = bvy = bvz = spin = 0;
        estado = ESPERANDO;
        pressao = 0;
        // defensores se reorganizam um pouco
        for (Ator d : atores)
            if (d.time == 1 && !d.goleiro) {
                d.barreira = false;
                d.ax = d.x + (bx - d.x) * 0.15f;
                d.ay = d.y + (by - d.y) * 0.15f;
            }
    }

    private void goleiro(float dt, float vel) {
        Ator g = null;
        for (Ator a : atores) if (a.goleiro) g = a;
        if (g == null) return;
        boolean vindo = bvx > 3;
        float t = vindo ? (L - bx) / bvx : 99;
        float yCruza = by + bvy * t;
        if (vindo && t < 1.6f && yCruza > GOL_Y1 - 2.5f && yCruza < GOL_Y2 + 2.5f) {
            float alvoY = Math.max(GOL_Y1 - 0.3f, Math.min(GOL_Y2 + 0.3f, yCruza));
            float v = 4 + g.gk / 100f * 4;
            float dy = alvoY - g.y;
            g.y += Math.signum(dy) * Math.min(Math.abs(dy), v * dt);
            if (!decidiuGoleiro && L - bx < 2.6f + vel * 0.05f) {
                decidiuGoleiro = true;
                float alcance = 1.0f + g.gk / 100f * 1.7f;
                float dist = Math.abs(by + bvy * ((g.x - bx) / Math.max(1, bvx)) - g.y);
                float altura = bz;
                if (dist < alcance && altura < 2.6f) {
                    float p = 0.55f + g.gk / 260f - (vel - 20) * 0.02f - dist / alcance * 0.4f - (altura > 1.8f ? 0.08f : 0);
                    p *= tipo == PENALTI ? 0.8f : 1;
                    p *= dif;
                    if (rnd.nextFloat() < Math.max(0.05f, Math.min(0.93f, p))) {
                        boolean segura = rnd.nextFloat() < 0.5f;
                        g.y = by;
                        if (segura) {
                            bvx = bvy = bvz = 0;
                            bx = g.x - 0.3f;
                            by = g.y;
                            bz = 1;
                            terminar("DEFESA!", g.j.nome + " segurou firme.", false);
                        } else {
                            bvx = -bvx * 0.3f;
                            bvy = (by > 34 ? 1 : -1) * 7;
                            bvz = 3;
                            terminar("DEFESA!", g.j.nome + " espalmou.", false);
                        }
                    }
                }
            }
        } else if (estado != FIM) {
            // fecha o ângulo
            float alvoY = 34 + (by - 34) * 0.35f;
            float alvoX = L - 1.2f - (L - bx < 18 && tipo != PENALTI && tipo != FALTA ? Math.min(4, (18 - (L - bx)) * 0.3f) : 0);
            g.y += (alvoY - g.y) * Math.min(1, dt * 2);
            g.x += (alvoX - g.x) * Math.min(1, dt * 1.5f);
        }
        // goleiro sai nos cruzamentos dentro da pequena área
        if (estado == VOANDO && !chuteNoGol && Math.hypot(g.x - bx, g.y - by) < 1.6f && bz < 2.6f && L - bx < 6) {
            bvx = bvy = bvz = 0;
            terminar("O goleiro ficou com ela", "", false);
        }
    }

    private void moverAtores(float dt, boolean esperando) {
        for (Ator a : atores) {
            if (a.goleiro) continue;
            float tx = a.ax, ty = a.ay, vmax = a.velMax();
            if (a.time == 1 && !esperando && estado == VOANDO && !a.barreira) {
                // defensor tenta cortar a trajetória
                float px = bx + bvx * 0.35f, py = by + bvy * 0.35f;
                if (Math.hypot(a.x - px, a.y - py) < 9) {
                    tx = px;
                    ty = py;
                }
            } else if (a.time == 1 && esperando && a != maisPerto(1, bx, by, true)) {
                vmax *= 0.25f;
            } else if (a.time == 0 && a != portador && esperando) {
                // companheiros se mexem devagar procurando espaço
                tx = a.ax + (float) Math.sin(a.fase * 0.3f) * 1.5f;
                vmax *= 0.3f;
            } else if (a.time == 1 && esperando) vmax *= tipo == CONTRA ? 0.42f : 0.3f;
            float dx = tx - a.x, dy = ty - a.y, d = (float) Math.hypot(dx, dy);
            float v = d < 0.2f ? 0 : Math.min(vmax, d * 2);
            float dvx = d > 0 ? dx / d * v : 0, dvy = d > 0 ? dy / d * v : 0;
            a.vx += (dvx - a.vx) * Math.min(1, dt * 6);
            a.vy += (dvy - a.vy) * Math.min(1, dt * 6);
            if (a == portador) {
                a.vx = a.vy = 0;
            }
            a.x += a.vx * dt;
            a.y += a.vy * dt;
        }
    }

    Ator maisPerto(int time, float x, float y, boolean semGoleiro) {
        Ator m = null;
        float md = 1e9f;
        for (Ator a : atores) {
            if (a.time != time || (semGoleiro && a.goleiro) || a == portador) continue;
            float d = (float) Math.hypot(a.x - x, a.y - y);
            if (d < md) {
                md = d;
                m = a;
            }
        }
        return m;
    }

    private void terminar(String r, String d, boolean g) {
        estado = FIM;
        resultado = r;
        detalhe = d;
        gol = g;
    }
}
