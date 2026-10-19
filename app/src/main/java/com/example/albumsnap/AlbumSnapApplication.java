package com.example.albumsnap;

import android.app.Application;

import com.example.albumsnap.storage.SettingsManager;

/** Aplica o tema e o idioma guardados assim que a app arranca. */
public class AlbumSnapApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsManager.from(this).applyAll();
    }
}
