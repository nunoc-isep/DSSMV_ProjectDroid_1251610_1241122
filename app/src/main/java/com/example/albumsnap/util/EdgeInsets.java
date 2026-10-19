package com.example.albumsnap.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Evita que o conteudo fique por baixo da barra de estado e da barra de navegacao. */
public final class EdgeInsets {

    private EdgeInsets() {
    }

    /** Soma o espaco das barras do sistema ao padding que a view ja tem. */
    public static void applyTo(final View root) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
                return insets;
            }
        });
    }
}
