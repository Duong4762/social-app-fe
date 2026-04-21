package com.example.social_app.data.api;

import com.example.social_app.data.model.Post;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface PostApiService {

    @POST("posts/create")
    Call<Post> createPost(
            @Header("Authorization") String token,
            @Body PostRequest request
    );

    @PUT("posts/update")
    Call<Post> updatePost(
            @Header("Authorization") String token,
            @Body PostRequest request
    );

    @DELETE("posts/{id}")
    Call<Void> deletePost(
            @Header("Authorization") String token,
            @Path("id") String postId
    );
}
