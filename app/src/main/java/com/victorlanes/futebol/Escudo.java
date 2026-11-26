package com.victorlanes.futebol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/** Escudo simples desenhado com as cores e a sigla do clube. */
public class Escudo extends View {
    private int c1, c2;
    private String sigla = "";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path forma = new Path();

    public Escudo(Context c) {
        super(c);
    }

    public Escudo de(int cor1, int cor2, String s) {
        c1 = cor1;
        c2 = cor2;
        sigla = s == null ? "" : s;
        invalidate();
        return this;
    }

    public Escudo de(Modelo.Clube c) {
        return de(c.cor1, c.cor2, c.sigla);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        forma.reset();
        forma.moveTo(w * .08f, h * .08f);
        forma.lineTo(w * .92f, h * .08f);
        forma.lineTo(w * .92f, h * .5f);
        forma.quadTo(w * .9f, h * .82f, w * .5f, h * .96f);
        forma.quadTo(w * .1f, h * .82f, w * .08f, h * .5f);
        forma.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(c1);
        c.drawPath(forma, p);
        c.save();
        c.clipPath(forma);
        p.setColor(c2);
        c.drawRect(w * .42f, 0, w * .58f, h, p);
        c.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * .06f);
        p.setColor(c2);
        c.drawPath(forma, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        p.setTextSize(h * .26f);
        p.setShadowLayer(w * .04f, 0, w * .015f, 0xCC000000);
        c.drawText(sigla, w * .5f, h * .56f, p);
        p.clearShadowLayer();
    }
}
