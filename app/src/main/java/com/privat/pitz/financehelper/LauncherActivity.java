package com.privat.pitz.financehelper;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Launch your actual main activity
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);

        // Finish the dummy launcher activity
        finish();
    }
}
