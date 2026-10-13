/*
 * Copyright 2017 hanks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * 来源：https://github.com/hanks-zyh/PasscodeView
 * 本项目做了包名迁移与 AndroidX 适配，详见 THIRD_PARTY_NOTICES.md
 */
package com.jianji.note.widget.passcode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * CircleView
 * Created by hanks on 2017/4/17.
 */
public class CircleView extends View {

    private Paint mPaint;
    private int color = Color.BLACK;

    public CircleView(Context context) {
        super(context);
        init();
    }

    public CircleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public int getColor() {
        return color;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        double width = (getWidth() - getPaddingLeft() - getPaddingRight()) * 0.5;
        double height = (getHeight() - getPaddingTop() - getPaddingBottom()) * 0.5;
        int cx = (int) (getPaddingLeft() + width);
        int cy = (int) (getPaddingTop() + height);
        int radius = (int) Math.min(width, height);
        mPaint.setColor(color);
        canvas.drawCircle(cx, cy, radius, mPaint);
    }
}
