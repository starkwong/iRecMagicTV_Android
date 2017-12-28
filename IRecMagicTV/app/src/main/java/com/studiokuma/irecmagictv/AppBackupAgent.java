package com.studiokuma.irecmagictv;

import android.app.backup.BackupAgentHelper;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * Created by Sheep on 2015/01/18.
 */
public class AppBackupAgent extends BackupAgentHelper {
    public static final String FILE_FAVORITES="favorites.json";
    public static final String FILE_RECORDINGS="recordings.json";
    public static final String FILE_HISTORY="history.json";
    public static final String FILE_ALERTS="alerts.json";

    @Override
    public void onCreate() {
        FileBackupHelper helper=new FileBackupHelper(this,FILE_FAVORITES,FILE_RECORDINGS,FILE_HISTORY,FILE_ALERTS);
        addHelper("fileSet",helper);

        SharedPreferencesBackupHelper helper2=new SharedPreferencesBackupHelper(this,IRecLibrary.class.getSimpleName());
        addHelper("prefSet",helper2);
    }
}
