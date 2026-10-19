package com.example.albumsnap.views;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.example.albumsnap.R;
import com.example.albumsnap.external.SpotifyAuthManager;
import com.example.albumsnap.storage.SettingsManager;
import com.example.albumsnap.util.EdgeInsets;

/** Definicoes: tema, idioma e terminar sessao. */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        EdgeInsets.applyTo(findViewById(R.id.settingsRoot));

        settings = SettingsManager.from(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RadioGroup radioTheme = findViewById(R.id.radioTheme);
        RadioGroup radioLanguage = findViewById(R.id.radioLanguage);

        // Estado atual, escolhido antes de ligar os listeners (para nao disparar mudancas)
        radioTheme.check(themeToId(settings.getTheme()));
        radioLanguage.check(languageToId(settings.getLanguage()));

        radioTheme.setOnCheckedChangeListener((group, checkedId) -> {
            settings.setTheme(idToTheme(checkedId));
            settings.applyTheme();          // recria o ecra com o novo tema
        });
        radioLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            settings.setLanguage(idToLanguage(checkedId));
            settings.applyLanguage();       // recria o ecra no novo idioma
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
    }

    private void logout() {
        SpotifyAuthManager.getInstance(this).disconnect();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static int themeToId(String theme) {
        if (SettingsManager.THEME_LIGHT.equals(theme)) return R.id.radioThemeLight;
        if (SettingsManager.THEME_DARK.equals(theme)) return R.id.radioThemeDark;
        return R.id.radioThemeSystem;
    }

    private static String idToTheme(int id) {
        if (id == R.id.radioThemeLight) return SettingsManager.THEME_LIGHT;
        if (id == R.id.radioThemeDark) return SettingsManager.THEME_DARK;
        return SettingsManager.THEME_SYSTEM;
    }

    private static int languageToId(String language) {
        if (SettingsManager.LANGUAGE_EN.equals(language)) return R.id.radioLangEn;
        if (SettingsManager.LANGUAGE_PT.equals(language)) return R.id.radioLangPt;
        return R.id.radioLangSystem;
    }

    private static String idToLanguage(int id) {
        if (id == R.id.radioLangEn) return SettingsManager.LANGUAGE_EN;
        if (id == R.id.radioLangPt) return SettingsManager.LANGUAGE_PT;
        return SettingsManager.LANGUAGE_SYSTEM;
    }
}
