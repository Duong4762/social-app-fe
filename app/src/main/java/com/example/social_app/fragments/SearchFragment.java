package com.example.social_app.fragments;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.social_app.R;

public class SearchFragment extends Fragment {
    private EditText searchInput;
    private TextView emptyState;
    private LinearLayout resultsContainer;

    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        searchInput = view.findViewById(R.id.etSearch);
        emptyState = view.findViewById(R.id.tvSearchEmpty);
        resultsContainer = view.findViewById(R.id.searchResultsContainer);

        searchInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                showResults(searchInput.getText().toString());
                return true;
            }
            return false;
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            showResults(searchInput.getText().toString());
            return true;
        });
    }

    private void showResults(String q) {
        String query = q == null ? "" : q.trim();
        resultsContainer.removeAllViews();

        if (query.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            emptyState.setText(R.string.search_empty_state);
            return;
        }

        emptyState.setVisibility(View.GONE);

        for (int i = 1; i <= 5; i++) {
            TextView item = new TextView(requireContext());
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            int padding = (int) (12 * getResources().getDisplayMetrics().density);
            item.setPadding(padding, padding, padding, padding);
            item.setBackgroundResource(R.drawable.bg_search_input);
            item.setText(getString(R.string.results_with_query, query) + " #" + i);
            item.setTextColor(getResources().getColor(R.color.text, null));

            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) item.getLayoutParams();
            params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
            item.setLayoutParams(params);
            resultsContainer.addView(item);
        }
    }
}
