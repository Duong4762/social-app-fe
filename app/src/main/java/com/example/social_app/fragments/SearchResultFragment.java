package com.example.social_app.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.social_app.R;

public class SearchResultFragment extends Fragment {
    private static final String ARG_QUERY = "query";

    public SearchResultFragment() {
        super(R.layout.activity_search_result);
    }

    public static SearchResultFragment newInstance(String query) {
        SearchResultFragment fragment = new SearchResultFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String query = "";
        if (getArguments() != null) {
            String value = getArguments().getString(ARG_QUERY);
            query = value == null ? "" : value;
        }

        TextView resultTitle = view.findViewById(R.id.tv_search_result_title);
        resultTitle.setText(getString(R.string.results_with_query, query));
    }
}
