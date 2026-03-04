package com.example.social_app;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.social_app.ui.noti.NotificationsFragment;
import com.example.social_app.ui.search.SearchFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        BottomNavigationView bottom = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            replace(new SearchFragment());
            bottom.setSelectedItemId(R.id.nav_search);
        }

        bottom.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_search) {
                replace(new SearchFragment());
                return true;
            } else if (id == R.id.nav_notifications) {
                replace(new NotificationsFragment());
                return true;
            } else {

                replace(PlaceholderFragment.newInstance(item.getTitle().toString()));
                return true;
            }
        });
    }

    private void replace(Fragment f) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, f)
                .commit();
    }
}