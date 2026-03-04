package com.example.social_app.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.social_app.R;

public class SearchFragment extends Fragment {

    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText et = view.findViewById(R.id.etSearch);
        ImageView btn = view.findViewById(R.id.btnSearch);

        btn.setOnClickListener(v -> openResult(et.getText().toString()));

        et.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                openResult(et.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void openResult(String q) {
        Intent i = new Intent(requireContext(), SearchResultActivity.class);
        i.putExtra("query", q == null ? "" : q.trim());
        startActivity(i);
    }
}