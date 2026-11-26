package com.victorlanes.futebol;

import com.victorlanes.futebol.Modelo.Clube;
import com.victorlanes.futebol.Modelo.Jogador;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Motor da partida (visão de cima). Campo de 105 x 68 m. O time 0 é sempre o do usuário e ataca para a direita (x = 105).
 */
public final class Jogo {

    public static final float L = 105, W = 68, GOL_Y1 = 34 - 3.66f, GOL_Y2 = 34 + 3.66f, TRAVE = 2.44f;
    public static final int JOGANDO = 0, PARADO = 1, GOL = 2, INTERVALO = 3, FIM = 4;

    public final Random rnd = new Random();

    public static final class P {
        public float x, y, vx, vy, hx, hy, fx = 1, fy = 0; // posição, velocidade, alvo, direção
        public int time, vaga, pos;
        public Jogador j;
        public boolean goleiro;
        public float cooldown, atordoado, decidir, carrinho;
        public int vel, fin, pas, dri, def, gk;

        float velMax() {
            return 5.2f + vel / 100f * 3.2f;
        }
    }

    public static final class Bola {
        public float x = L / 2, y = W / 2, z, vx, vy, vz;
        public P dono, ultimoToque, chutador, alvo;
        public int ultimoTime = -1;
        public boolean chute; // está em trajetória de chute
        public int defesa = -1; // -1 indefinido, 0 goleiro falha, 1 defende, 2 espalma
    }

    public final List<P> ps = new ArrayList<>();
    public final Bola bola = new Bola();
    public final Clube[] clubes = new Clube[2];
    public int[] placar = new int[2];
    public float tempoReal, duracao; // segundos
    public int estado = PARADO;
    public float timerEstado = 1.2f;
    public String aviso = "Bola rolando!";
    public float avisoT = 1.6f;
    public P controlado;
    public boolean semUsuario; // todos controlados pela IA (testes)
    public final List<String> autores = new ArrayList<>(); // "0Nome" / "1Nome"
    public int[] chutes = new int[2], posseT = new int[2];
    public int nPasses, nDesarmes, nTrocas, nFora;
    public float[] posse = new float[2];
    public boolean segundoTempo;
    private int reinicioTime = -1; // quem cobra a bola parada
    private P cobrador;
    private float dificuldade;

    // entrada do usuário (preenchida pela view)
    public float jx, jy; // joystick -1..1
    public boolean correr;
    public float cargaChute = -1; // >=0 enquanto segura o botão de chute
    private boolean pedePasse, pedeLancamento, pedeChute, pedeTrocar, pedeCarrinho;
    private float forcaChute;

    public Jogo(Clube meu, Clube adv, int minutos, int dif) {
        clubes[0] = meu;
        clubes[1] = adv;
        duracao = Math.max(2, minutos) * 60f;
        dificuldade = dif == 0 ? .75f : dif == 2 ? 1.25f : 1f;
        for (int t = 0; t < 2; t++) {
            Clube c = clubes[t];
            c.validar();
            float[][] v = Modelo.vagas(c.formacao);
            for (int i = 0; i < 11; i++) {
                P p = new P();
                p.time = t;
                p.vaga = i;
                p.j = c.jogador(c.titular[i]);
                p.pos = (int) v[i][2];
                p.goleiro = i == 0;
                int pen = p.j.pos == p.pos ? 0 : 6;
                float bonus = t == 1 ? (dificuldade - 1) * 12 : 0;
                p.vel = cl(p.j.a[0] - pen + bonus);
                p.fin = cl(p.j.a[1] - pen + bonus);
                p.pas = cl(p.j.a[2] - pen + bonus);
                p.dri = cl(p.j.a[3] - pen + bonus);
                p.def = cl(p.j.a[4] - pen + bonus);
                p.gk = cl(p.j.a[5] + bonus);
                ps.add(p);
            }
        }
        saidaDeBola(1);
    }

    static int cl(float v) {
        return Math.max(10, Math.min(99, Math.round(v)));
    }

    // ================================================================== comandos

    public void passe() {
        pedePasse = true;
    }

    public void lancamento() {
        pedeLancamento = true;
    }

    public void soltarChute() {
        forcaChute = Math.min(1f, Math.max(0.15f, cargaChute));
        cargaChute = -1;
        pedeChute = true;
    }

    public void trocar() {
        pedeTrocar = true;
    }

    public void carrinho() {
        pedeCarrinho = true;
    }

    public boolean ataque() {
        return bola.dono != null && bola.dono.time == 0;
    }

    public int minuto() {
        return Math.min(90, (int) (tempoReal / duracao * 90));
    }

    // ================================================================== posições

    float meuGolX(int t) {
        return t == 0 ? 0 : L;
    }

    float golAlvoX(int t) {
        return t == 0 ? L : 0;
    }

    /** posição tática da vaga dada a situação do jogo */
    void posicaoTatica(P p, boolean comBola) {
        float[][] v = Modelo.vagas(clubes[p.time].formacao);
        float prof = v[p.vaga][0], larg = v[p.vaga][1];
        float bx = p.time == 0 ? bola.x : L - bola.x;
        float by = bola.y;
        if (p.goleiro) {
            float gy = 34 + Math.max(-5f, Math.min(5f, (by - 34) * 0.25f));
            float gx = 1.5f + Math.max(0, Math.min(4f, (bx - 20) * 0.05f));
            p.hx = p.time == 0 ? gx : L - gx;
            p.hy = gy;
            return;
        }
        float x = 4 + prof * 62 + (bx - 52.5f) * 0.6f + (comBola ? 10 : -3);
        if (comBola) x += p.pos == 5 ? 12 : p.pos == 4 ? 6 : p.pos == 2 ? 4 : 0;
        x = Math.max(6, Math.min(comBola ? 100 : 92, x));
        float y = 5 + larg * 58 + (by - 34) * (comBola ? .18f : .3f);
        y = Math.max(2, Math.min(W - 2, y));
        p.hx = p.time == 0 ? x : L - x;
        p.hy = y;
    }

    void posicionarSaida(int quemSai) {
        for (P p : ps) {
            float[][] v = Modelo.vagas(clubes[p.time].formacao);
            float x = 2 + v[p.vaga][0] * 46;
            if (p.goleiro) x = 1.5f;
            float y = 5 + v[p.vaga][1] * 58;
            p.x = p.time == 0 ? x : L - x;
            p.y = y;
            p.vx = p.vy = 0;
            p.atordoado = 0;
            p.carrinho = 0;
        }
        // os dois atacantes do time que sai ficam no círculo central
        P a = maisProximo(quemSai, L / 2, W / 2, true, null);
        a.x = L / 2 + (quemSai == 0 ? -0.6f : 0.6f);
        a.y = W / 2;
        P b = maisProximo(quemSai, L / 2, W / 2, true, a);
        b.x = L / 2 + (quemSai == 0 ? -3 : 3);
        b.y = W / 2 + 6;
    }

    void saidaDeBola(int quem) {
        posicionarSaida(quem);
        bola.x = L / 2;
        bola.y = W / 2;
        bola.z = bola.vx = bola.vy = bola.vz = 0;
        bola.chute = false;
        P a = maisProximo(quem, L / 2, W / 2, true, null);
        darBola(a);
        reinicio(quem, a, 1.0f);
        if (quem == 0) controlado = a;
        else if (controlado == null || controlado.goleiro) controlado = maisProximo(0, L / 2, W / 2, true, null);
    }

    void reinicio(int time, P quem, float pausa) {
        estado = PARADO;
        timerEstado = pausa;
        reinicioTime = time;
        cobrador = quem;
    }

    P maisProximo(int time, float x, float y, boolean semGoleiro, P exceto) {
        P m = null;
        float md = Float.MAX_VALUE;
        for (P p : ps) {
            if (p.time != time || p == exceto || (semGoleiro && p.goleiro)) continue;
            float d = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y);
            if (d < md) {
                md = d;
                m = p;
            }
        }
        return m;
    }

    void darBola(P p) {
        if (bola.ultimoTime >= 0 && bola.ultimoTime != p.time) nTrocas++;
        bola.dono = p;
        bola.alvo = null;
        bola.ultimoToque = p;
        bola.ultimoTime = p.time;
        bola.chute = false;
        bola.defesa = -1;
        bola.z = 0;
        bola.vz = 0;
        if (p.time == 0 && !p.goleiro) controlado = p;
    }

    // ================================================================== laço principal

    public void passo(float dt) {
        if (estado == FIM) return;
        if (avisoT > 0) avisoT -= dt;
        if (estado == GOL || estado == INTERVALO) {
            timerEstado -= dt;
            if (timerEstado <= 0) {
                if (estado == INTERVALO) {
                    segundoTempo = true;
                    saidaDeBola(0);
                } else saidaDeBola(ultimoGolContra);
            }
            moverJogadores(dt, true);
            return;
        }
        tempoReal += dt;
        if (!segundoTempo && tempoReal >= duracao / 2) {
            estado = INTERVALO;
            timerEstado = 2.5f;
            mostrar("Intervalo", 2.5f);
            return;
        }
        if (tempoReal >= duracao) {
            estado = FIM;
            mostrar("Fim de jogo!", 99);
            return;
        }
        if (bola.dono != null) posse[bola.dono.time] += dt;

        if (estado == PARADO) {
            timerEstado -= dt;
            if (cobrador != null && bola.dono == cobrador) {
                cobrador.vx = cobrador.vy = 0;
                if (reinicioTime == 0 && cobrador == controlado && !semUsuario) {
                    // usuário cobra: espera um comando (ou cobra sozinho depois de 3 s)
                    if (pedePasse || pedeLancamento || pedeChute || timerEstado < -3f) {
                        estado = JOGANDO;
                        if (!pedePasse && !pedeLancamento && !pedeChute) pedePasse = true;
                    }
                } else if (timerEstado <= 0) {
                    estado = JOGANDO;
                    cobrador.decidir = 0;
                    aiForcarPasse = true;
                }
            } else if (timerEstado <= 0) estado = JOGANDO;
            moverJogadores(dt, true);
            if (estado == PARADO) {
                limparComandos();
                return;
            }
        }

        atualizarControle();
        acoesUsuario();
        for (P p : ps) {
            if (p.cooldown > 0) p.cooldown -= dt;
            if (p.atordoado > 0) p.atordoado -= dt;
            if (p.carrinho > 0) p.carrinho -= dt;
        }
        ia(dt);
        moverJogadores(dt, false);
        moverBola(dt);
        limparComandos();
    }

    boolean aiForcarPasse;
    int ultimoGolContra;

    void limparComandos() {
        pedePasse = pedeLancamento = pedeChute = pedeTrocar = pedeCarrinho = false;
    }

    void mostrar(String s, float t) {
        aviso = s;
        avisoT = t;
    }

    // ================================================================== usuário

    void atualizarControle() {
        if (bola.dono != null && bola.dono.time == 0 && !bola.dono.goleiro) controlado = bola.dono;
        if (controlado == null || controlado.goleiro) controlado = maisProximo(0, bola.x, bola.y, true, null);
        if (pedeTrocar) {
            P n = maisProximo(0, bola.x, bola.y, true, controlado);
            P m = maisProximo(0, bola.x, bola.y, true, null);
            controlado = m != controlado ? m : n;
        }
        // perdeu a bola e está longe: passa o controle para quem está mais perto
        if (bola.dono != null && bola.dono.time == 1) {
            P m = maisProximo(0, bola.x, bola.y, true, null);
            if (m != controlado && dist(controlado, bola.x, bola.y) > dist(m, bola.x, bola.y) + 10) controlado = m;
        }
    }

    void acoesUsuario() {
        P c = semUsuario ? null : controlado;
        if (c == null) return;
        if (bola.dono == c) {
            if (pedeChute) chutar(c, forcaChute, jy);
            else if (pedePasse) {
                P alvo = alvoPasse(c, jx, jy);
                if (alvo != null) passar(c, alvo, false);
            } else if (pedeLancamento) {
                P alvo = alvoPasse(c, jx, jy);
                if (alvo != null) passar(c, alvo, true);
            }
        } else {
            if (pedeCarrinho && c.carrinho <= 0 && c.atordoado <= 0) {
                c.carrinho = 0.45f;
                float dx = bola.x - c.x, dy = bola.y - c.y, d = (float) Math.hypot(dx, dy);
                if (d > 0.1f) {
                    c.vx = dx / d * 10;
                    c.vy = dy / d * 10;
                }
                if (bola.dono != null && bola.dono.time == 1 && d < 2.4f) disputa(c, bola.dono, 0.15f);
                else if (bola.dono == null && d < 2.4f && bola.z < 1) tocarSolta(c);
            }
            if (pedeChute && bola.dono == null && dist(c, bola.x, bola.y) < 1.6f && bola.z < 1.5f) {
                darBola(c);
                chutar(c, forcaChute, jy);
            }
        }
    }

    /** companheiro mais bem posicionado na direção apontada */
    P alvoPasse(P de, float dx, float dy) {
        float m = (float) Math.hypot(dx, dy);
        if (m < 0.2f) {
            dx = de.fx;
            dy = de.fy;
        } else {
            dx /= m;
            dy /= m;
        }
        P melhor = null;
        float ms = -1e9f;
        for (P p : ps) {
            if (p.time != de.time || p == de) continue;
            float rx = p.x - de.x, ry = p.y - de.y, d = (float) Math.hypot(rx, ry);
            if (d < 3 || d > 55) continue;
            float cos = (rx * dx + ry * dy) / d;
            float s = cos * 3 - d / 25f - pressao(p) * 0.8f;
            if (p.goleiro) s -= 2;
            if (s > ms) {
                ms = s;
                melhor = p;
            }
        }
        return melhor;
    }

    float pressao(P p) {
        float s = 0;
        for (P o : ps) if (o.time != p.time) {
            float d = dist(o, p.x, p.y);
            if (d < 6) s += (6 - d) / 6;
        }
        return s;
    }

    static float dist(P p, float x, float y) {
        return (float) Math.hypot(p.x - x, p.y - y);
    }

    // ================================================================== ações com a bola

    void passar(P de, P alvo, boolean alto) {
        float t0 = 0.6f;
        float lx = alvo.x + alvo.vx * t0, ly = alvo.y + alvo.vy * t0;
        float dx = lx - bola.x, dy = ly - bola.y, d = (float) Math.hypot(dx, dy);
        if (d < 0.5f) return;
        float erro = (1 - de.pas / 100f) * 0.16f * (rnd.nextFloat() * 2 - 1);
        double ang = Math.atan2(dy, dx) + erro;
        float v = alto ? Math.max(11, Math.min(24, d * 0.55f + 8)) : Math.max(9, Math.min(27, d * 0.9f + 7));
        bola.vx = (float) Math.cos(ang) * v;
        bola.vy = (float) Math.sin(ang) * v;
        bola.vz = alto ? Math.min(12, 3 + d * 0.22f) : 0.4f;
        soltar(de);
        bola.alvo = alvo;
        nPasses++;
        if (alvo.time == 0 && !alvo.goleiro && de.time == 0) controlado = alvo;
    }

    void chutar(P de, float forca, float mira) {
        float gx = golAlvoX(de.time);
        float alvoY;
        if (de.time == 0) alvoY = 34 + Math.max(-1f, Math.min(1f, mira)) * 3.1f;
        else alvoY = 34 + (rnd.nextBoolean() ? 1 : -1) * (1.2f + rnd.nextFloat() * 2.2f);
        float dx = gx - bola.x, dy = alvoY - bola.y, d = (float) Math.hypot(dx, dy);
        float erro = ((1 - de.fin / 100f) * 0.14f + forca * 0.05f + Math.min(0.12f, d / 400f)) * (rnd.nextFloat() * 2 - 1);
        if (de.time == 1) erro *= 1.1f / dificuldade;
        double ang = Math.atan2(dy, dx) + erro;
        float v = 17 + forca * 15 + de.fin / 100f * 5;
        bola.vx = (float) Math.cos(ang) * v;
        bola.vy = (float) Math.sin(ang) * v;
        float tempo = d / v;
        bola.vz = Math.max(0.5f, forca * 5.2f + (rnd.nextFloat() - 0.5f) * 1.2f) * Math.min(1.3f, 0.6f + tempo);
        soltar(de);
        bola.chute = true;
        bola.chutador = de;
        bola.defesa = -1;
        chutes[de.time]++;
    }

    void soltar(P de) {
        bola.dono = null;
        bola.ultimoToque = de;
        bola.ultimoTime = de.time;
        de.cooldown = 0.35f;
        bola.chute = false;
    }

    /** desarme: atacante x defensor */
    void disputa(P defensor, P atacante, float extra) {
        float chance = 0.5f + (defensor.def - atacante.dri) / 160f + extra;
        if (defensor.time == 1) chance *= dificuldade;
        if (rnd.nextFloat() < chance) {
            nDesarmes++;
            bola.dono = null;
            bola.ultimoTime = defensor.time;
            bola.ultimoToque = defensor;
            float a = (float) (rnd.nextFloat() * Math.PI * 2);
            bola.vx = defensor.fx * 6 + (float) Math.cos(a) * 2;
            bola.vy = defensor.fy * 6 + (float) Math.sin(a) * 2;
            atacante.atordoado = 0.6f;
            atacante.cooldown = 0.5f;
            if (rnd.nextFloat() < 0.6f) darBola(defensor);
        } else {
            defensor.atordoado = defensor.carrinho > 0 ? 0.9f : 0.5f;
        }
    }

    void tocarSolta(P p) {
        bola.vx = p.fx * 8;
        bola.vy = p.fy * 8;
        bola.ultimoTime = p.time;
        bola.ultimoToque = p;
    }

    // ================================================================== inteligência

    void ia(float dt) {
        boolean[] comBola = {bola.dono != null && bola.dono.time == 0, bola.dono != null && bola.dono.time == 1};
        for (int t = 0; t < 2; t++) {
            // quem vai na bola
            P caca = null, apoio = null;
            if (!comBola[t]) {
                float px = bola.x + bola.vx * 0.35f, py = bola.y + bola.vy * 0.35f;
                caca = maisProximo(t, px, py, true, t == 0 && !semUsuario ? controlado : null);
                apoio = maisProximo(t, px, py, true, t == 0 ? controlado : caca);
                if (t == 0 && !semUsuario && controlado != null && caca != null && dist(caca, bola.x, bola.y) > dist(controlado, bola.x, bola.y) - 4) caca = null;
                if (t == 0 && !semUsuario && apoio != null && dist(apoio, bola.x, bola.y) > 18) apoio = null;
            }
            for (P p : ps) {
                if (p.time != t) continue;
                posicaoTatica(p, comBola[t]);
                if (p == controlado && !semUsuario) continue;
                if (p.atordoado > 0) {
                    p.vx *= 0.9f;
                    p.vy *= 0.9f;
                    continue;
                }
                if (bola.dono == p) {
                    iaComBola(p, dt);
                    continue;
                }
                if (p.goleiro) {
                    iaGoleiro(p);
                    continue;
                }
                float tx = p.hx, ty = p.hy, rapidez = 0.75f;
                if (p == caca) {
                    tx = bola.x + bola.vx * 0.3f;
                    ty = bola.y + bola.vy * 0.3f;
                    rapidez = 1.05f;
                    if (bola.dono != null && dist(p, bola.dono.x, bola.dono.y) < 1.3f && p.atordoado <= 0) {
                        float taxa = 0.7f * (t == 1 ? dificuldade : 0.8f);
                        if (rnd.nextFloat() < taxa * dt) disputa(p, bola.dono, 0);
                    }
                } else if (p == apoio && bola.dono != null) {
                    // marca de longe, fechando o caminho até o gol
                    float gx = meuGolX(t);
                    tx = bola.x + (gx - bola.x) * 0.15f;
                    ty = bola.y + (34 - bola.y) * 0.15f;
                    rapidez = 0.9f;
                } else if (!comBola[t] && bola.dono != null) {
                    // marcação por zona: aproxima do atacante mais perto da sua posição
                    P adv = maisProximo(1 - t, p.hx, p.hy, true, null);
                    if (adv != null && dist(adv, p.hx, p.hy) < 12) {
                        float gx = meuGolX(t);
                        tx = adv.x + (gx - adv.x) * 0.08f;
                        ty = adv.y + (34 - adv.y) * 0.08f;
                    }
                }
                andar(p, tx, ty, rapidez);
            }
        }
    }

    void andar(P p, float tx, float ty, float rapidez) {
        float dx = tx - p.x, dy = ty - p.y, d = (float) Math.hypot(dx, dy);
        float vmax = p.velMax() * rapidez;
        float v = d < 0.4f ? 0 : Math.min(vmax, d * 2.2f);
        float dvx = d > 0 ? dx / d * v : 0, dvy = d > 0 ? dy / d * v : 0;
        p.vx += (dvx - p.vx) * 0.12f;
        p.vy += (dvy - p.vy) * 0.12f;
    }

    void iaComBola(P p, float dt) {
        p.decidir -= dt;
        float gx = golAlvoX(p.time);
        float dg = (float) Math.hypot(gx - p.x, 34 - p.y);
        float press = pressao(p);
        if (p.goleiro) {
            p.vx = p.vy = 0;
            if (p.decidir > 0) return;
            P alvo = null;
            float ms = -1e9f;
            for (P o : ps) if (o.time == p.time && o != p) {
                float s = -pressao(o) * 2 + (p.time == 0 ? o.x : L - o.x) / 30f - Math.abs(dist(o, p.x, p.y) - 25) / 15f;
                if (s > ms) {
                    ms = s;
                    alvo = o;
                }
            }
            if (alvo != null) passar(p, alvo, dist(alvo, p.x, p.y) > 25);
            return;
        }
        if (aiForcarPasse) {
            aiForcarPasse = false;
            P alvo = melhorPasse(p);
            if (alvo != null) {
                passar(p, alvo, dist(alvo, p.x, p.y) > 28);
                return;
            }
        }
        if (p.decidir <= 0) {
            p.decidir = 0.25f + rnd.nextFloat() * 0.3f;
            float angulo = Math.abs(34 - p.y) / Math.max(1, Math.abs(gx - p.x));
            if (dg < 28 && angulo < 1.4f && (dg < 17 || rnd.nextFloat() < (dg < 22 ? 0.4f : 0.18f))) {
                chutar(p, Math.min(1f, 0.35f + dg / 35f), 0);
                return;
            }
            P alvo = melhorPasse(p);
            float avanco = alvo == null ? -99 : (p.time == 0 ? alvo.x - p.x : p.x - alvo.x);
            boolean livre = !adversarioNaFrente(p, 7);
            if (alvo != null && ((press > 1.1f && !livre) || (avanco > 8 && rnd.nextFloat() < (livre ? 0.2f : 0.5f)) || rnd.nextFloat() < (livre ? 0.03f : 0.1f))) {
                passar(p, alvo, dist(alvo, p.x, p.y) > 30 && rnd.nextFloat() < 0.6f);
                return;
            }
        }
        // conduz para o gol desviando de quem está na frente
        float tx = gx, ty = 34 + (p.y - 34) * 0.4f;
        P adv = maisProximo(1 - p.time, p.x, p.y, false, null);
        if (adv != null) {
            float d = dist(adv, p.x, p.y);
            float ax = adv.x - p.x;
            boolean frente = p.time == 0 ? ax > 0 : ax < 0;
            if (d < 5 && frente) ty = p.y + (adv.y > p.y ? -8 : 8);
        }
        andar(p, tx, Math.max(3, Math.min(W - 3, ty)), 0.85f);
    }

    boolean adversarioNaFrente(P p, float alcance) {
        for (P o : ps) {
            if (o.time == p.time) continue;
            float ax = p.time == 0 ? o.x - p.x : p.x - o.x;
            if (ax > 0 && ax < alcance && Math.abs(o.y - p.y) < alcance * 0.6f) return true;
        }
        return false;
    }

    P melhorPasse(P de) {
        P melhor = null;
        float ms = -1e9f;
        for (P o : ps) {
            if (o.time != de.time || o == de || o.goleiro) continue;
            float d = dist(o, de.x, de.y);
            if (d < 5 || d > 45) continue;
            float avanco = de.time == 0 ? o.x - de.x : de.x - o.x;
            float s = avanco / 8f - pressao(o) * 0.9f - d / 45f + rnd.nextFloat() * 0.6f;
            if (bloqueado(de, o)) s -= 2;
            if (s > ms) {
                ms = s;
                melhor = o;
            }
        }
        return melhor;
    }

    boolean bloqueado(P de, P para) {
        float dx = para.x - de.x, dy = para.y - de.y, d2 = dx * dx + dy * dy;
        for (P o : ps) {
            if (o.time == de.time) continue;
            float t = ((o.x - de.x) * dx + (o.y - de.y) * dy) / d2;
            if (t < 0.1f || t > 0.9f) continue;
            float px = de.x + dx * t, py = de.y + dy * t;
            if (Math.hypot(o.x - px, o.y - py) < 1.6f) return true;
        }
        return false;
    }

    void iaGoleiro(P g) {
        float tx = g.hx, ty = g.hy;
        float gx = meuGolX(g.time);
        boolean vindo = bola.chute && (g.time == 0 ? bola.vx < 0 : bola.vx > 0);
        if (vindo) {
            float t = (gx - bola.x) / bola.vx;
            if (t > 0 && t < 2) {
                float yCruza = bola.y + bola.vy * t;
                ty = Math.max(GOL_Y1 - 0.5f, Math.min(GOL_Y2 + 0.5f, yCruza));
                tx = gx + (g.time == 0 ? 1.2f : -1.2f);
                if (bola.defesa < 0) decidirDefesa(g, yCruza);
            }
        } else if (bola.dono == null && Math.abs(bola.x - gx) < 16.5f && Math.abs(bola.y - 34) < 20 && bola.z < 2) {
            // sai do gol para pegar a bola solta na área
            P perto = maisProximo(1 - g.time, bola.x, bola.y, true, null);
            if (perto == null || dist(perto, bola.x, bola.y) > dist(g, bola.x, bola.y) - 2) {
                tx = bola.x;
                ty = bola.y;
            }
        }
        andar(g, tx, ty, 1.1f);
    }

    void decidirDefesa(P g, float yCruza) {
        float vel = (float) Math.hypot(bola.vx, bola.vy);
        float canto = Math.abs(yCruza - 34) / 3.66f; // 0 centro, 1 trave
        float chance = 0.38f + g.gk / 100f * 0.6f - (vel - 22) * 0.018f - canto * 0.22f;
        if (bola.chutador != null) chance -= (bola.chutador.fin - 60) / 400f;
        if (g.time == 1) chance *= 0.92f + (dificuldade - 1) * 0.3f;
        else chance *= 0.95f;
        chance = Math.max(0.08f, Math.min(0.92f, chance));
        if (rnd.nextFloat() < chance) bola.defesa = rnd.nextFloat() < 0.55f ? 1 : 2;
        else bola.defesa = 0;
    }

    // ================================================================== física

    void moverJogadores(float dt, boolean parado) {
        for (P p : ps) {
            if (parado) {
                if (p != cobrador) andar(p, p.hx == 0 ? p.x : p.hx, p.hy == 0 ? p.y : p.hy, 0.6f);
                else {
                    p.vx = p.vy = 0;
                }
                if (estado == PARADO) {
                    // ninguém do outro time fica colado na cobrança
                    if (p.time != reinicioTime && dist(p, bola.x, bola.y) < 7) {
                        float dx = p.x - bola.x, dy = p.y - bola.y, d = Math.max(.1f, (float) Math.hypot(dx, dy));
                        p.x = bola.x + dx / d * 7;
                        p.y = bola.y + dy / d * 7;
                    }
                }
            } else if (p == controlado && !semUsuario && p.atordoado <= 0 && p.carrinho <= 0) {
                float m = (float) Math.hypot(jx, jy);
                float v = p.velMax() * (correr ? 1.25f : 0.95f) * (bola.dono == p ? 0.92f : 1f);
                float dvx = m > 0.15f ? jx / Math.max(1, m) * v : 0, dvy = m > 0.15f ? jy / Math.max(1, m) * v : 0;
                p.vx += (dvx - p.vx) * 0.2f;
                p.vy += (dvy - p.vy) * 0.2f;
            } else if (p.carrinho > 0) {
                p.vx *= 0.93f;
                p.vy *= 0.93f;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.x = Math.max(-2, Math.min(L + 2, p.x));
            p.y = Math.max(-1.5f, Math.min(W + 1.5f, p.y));
            float sp = (float) Math.hypot(p.vx, p.vy);
            if (sp > 0.5f) {
                p.fx = p.vx / sp;
                p.fy = p.vy / sp;
            }
        }
        // separa jogadores que se sobrepõem
        for (int i = 0; i < ps.size(); i++)
            for (int k = i + 1; k < ps.size(); k++) {
                P a = ps.get(i), b = ps.get(k);
                float dx = b.x - a.x, dy = b.y - a.y, d = (float) Math.hypot(dx, dy);
                if (d < 1.1f && d > 0.01f) {
                    float e = (1.1f - d) / 2;
                    a.x -= dx / d * e;
                    a.y -= dy / d * e;
                    b.x += dx / d * e;
                    b.y += dy / d * e;
                }
            }
    }

    void moverBola(float dt) {
        Bola b = bola;
        if (b.dono != null) {
            P p = b.dono;
            float ox = p.fx * 0.75f, oy = p.fy * 0.75f;
            b.x += ((p.x + ox) - b.x) * 0.5f;
            b.y += ((p.y + oy) - b.y) * 0.5f;
            b.vx = p.vx;
            b.vy = p.vy;
            b.z = 0;
            verificarLinhas();
            return;
        }
        b.x += b.vx * dt;
        b.y += b.vy * dt;
        b.z += b.vz * dt;
        if (b.z > 0) b.vz -= 9.8f * dt;
        if (b.z <= 0) {
            b.z = 0;
            if (b.vz < -1.5f) b.vz = -b.vz * 0.45f;
            else b.vz = 0;
            float atr = (float) Math.exp(-1.1f * dt);
            b.vx *= atr;
            b.vy *= atr;
        } else {
            float atr = (float) Math.exp(-0.15f * dt);
            b.vx *= atr;
            b.vy *= atr;
        }
        if (Math.hypot(b.vx, b.vy) < 6) b.chute = false;

        // goleiro em cima da trajetória do chute
        for (P g : ps) {
            if (!g.goleiro || b.defesa <= 0 || !b.chute) continue;
            if (dist(g, b.x, b.y) < 1.9f && b.z < 2.6f && g.time != b.ultimoTime) {
                if (b.defesa == 1) {
                    darBola(g);
                    g.decidir = 1.2f;
                    mostrar("Defesa do goleiro!", 1.2f);
                } else {
                    b.vx = -b.vx * 0.35f + (rnd.nextFloat() - .5f) * 4;
                    b.vy = (b.y > g.y ? 1 : -1) * (6 + rnd.nextFloat() * 5);
                    b.vz = 2;
                    b.chute = false;
                    b.ultimoTime = g.time;
                    b.ultimoToque = g;
                    mostrar("Espalmou!", 1.2f);
                }
                return;
            }
        }

        // alguém domina a bola
        float vb = (float) Math.hypot(b.vx, b.vy);
        P melhor = null;
        float md = 99;
        for (P p : ps) {
            if (p.cooldown > 0 || p.atordoado > 0) continue;
            float alcance = p.goleiro && naArea(p) ? 1.6f : 1.0f;
            float d = dist(p, b.x, b.y);
            float alt = p.goleiro && naArea(p) ? 2.6f : 1.4f;
            if (d < alcance && b.z < alt && d < md) {
                md = d;
                melhor = p;
            }
        }
        if (melhor != null) {
            boolean chuteForte = b.chute && vb > 18 && melhor.time != b.ultimoTime && !melhor.goleiro;
            if (chuteForte) {
                if (rnd.nextFloat() < 0.18f) { // bloqueio: desvia
                    b.vx *= -0.3f;
                    b.vy = b.vy * 0.3f + (rnd.nextFloat() - .5f) * 10;
                    b.chute = false;
                    b.ultimoTime = melhor.time;
                    melhor.cooldown = 0.3f;
                }
            } else if (!(melhor.goleiro && b.chute && b.defesa == 0 && melhor.time != b.ultimoTime)) {
                float dominio = vb < 14 ? 1 : 0.55f + melhor.dri / 250f;
                if (b.alvo != null && melhor.time != b.ultimoTime && vb > 7 && !melhor.goleiro)
                    dominio = 0.3f + melhor.def / 300f; // interceptar um passe é mais difícil
                if (rnd.nextFloat() < dominio) darBola(melhor);
                else melhor.cooldown = 0.25f;
            }
        }
        verificarLinhas();
    }

    boolean naArea(P p) {
        float gx = meuGolX(p.time);
        return Math.abs(p.x - gx) < 16.5f && Math.abs(p.y - 34) < 20.2f;
    }

    void verificarLinhas() {
        Bola b = bola;
        if (b.x > L || b.x < 0) {
            boolean direita = b.x > L;
            int atacante = direita ? 0 : 1; // quem ataca esse gol
            if (b.y > GOL_Y1 && b.y < GOL_Y2 && b.z < TRAVE) {
                gol(atacante);
                return;
            }
            int defensor = 1 - atacante;
            if (b.ultimoTime == defensor) { // escanteio
                float cy = b.y < 34 ? 0.5f : W - 0.5f;
                bolaParada(atacante, direita ? L - 0.5f : 0.5f, cy, "Escanteio");
            } else { // tiro de meta
                P g = null;
                for (P p : ps) if (p.goleiro && p.time == defensor) g = p;
                b.x = direita ? L - 5.5f : 5.5f;
                b.y = 34;
                b.vx = b.vy = b.vz = b.z = 0;
                g.x = b.x;
                g.y = b.y;
                darBola(g);
                g.decidir = 0.8f;
                reinicio(defensor, g, 1.2f);
                mostrar("Tiro de meta", 1.2f);
            }
            return;
        }
        if (b.y < 0 || b.y > W) {
            bolaParada(1 - b.ultimoTime, Math.max(1, Math.min(L - 1, b.x)), b.y < 0 ? 0.3f : W - 0.3f, "Lateral");
        }
    }

    void bolaParada(int time, float x, float y, String nome) {
        nFora++;
        bola.x = x;
        bola.y = y;
        bola.vx = bola.vy = bola.vz = bola.z = 0;
        bola.chute = false;
        P p = maisProximo(time, x, y, true, null);
        p.x = x - (y < 34 ? 0 : 0);
        p.y = y + (y < 34 ? -0.6f : 0.6f);
        darBola(p);
        if (time == 0) controlado = p;
        reinicio(time, p, 1.0f);
        mostrar(nome, 1.2f);
    }

    void gol(int time) {
        placar[time]++;
        P autor = bola.ultimoToque;
        String nome = autor != null && autor.time == time ? autor.j.nome : (autor != null ? autor.j.nome + " (contra)" : "");
        if (autor != null && autor.time == time) {
            autores.add(time + autor.j.nome);
            autor.j.gols++;
            autor.j.golsCarreira++;
        }
        else autores.add(time + "");
        mostrar("GOOOL! " + nome, 2.6f);
        estado = GOL;
        timerEstado = 2.6f;
        ultimoGolContra = 1 - time;
        bola.dono = null;
        bola.vx *= 0.2f;
        bola.vy *= 0.2f;
    }
}
