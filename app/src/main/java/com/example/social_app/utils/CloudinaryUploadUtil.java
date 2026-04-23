package com.example.social_app.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class CloudinaryUploadUtil {

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static String cloudName = "dt52s8jxo";
    private static String uploadPreset = "ml_default";

    private CloudinaryUploadUtil() {
        // Utility class
    }

    public interface UploadCallback {
        void onSuccess(String secureUrl, String publicId);
        void onError(String message, Throwable throwable);
    }

    public static void setCloudinaryConfig(@NonNull String cloudNameValue, @NonNull String uploadPresetValue) {
        cloudName = cloudNameValue != null ? cloudNameValue.trim() : "";
        uploadPreset = uploadPresetValue != null ? uploadPresetValue.trim() : "";
    }

    public static void uploadMedia(
            @NonNull Context context,
            @NonNull Uri mediaUri,
            @NonNull UploadCallback callback
    ) {
        uploadMedia(context, mediaUri, cloudName, uploadPreset, callback);
    }

    public static void uploadMedia(
            @NonNull Context context,
            @NonNull Uri mediaUri,
            @NonNull String cloudName,
            @NonNull String uploadPreset,
            @NonNull UploadCallback callback
    ) {
        String resolvedCloudName = cloudName != null ? cloudName.trim() : "";
        String resolvedUploadPreset = uploadPreset != null ? uploadPreset.trim() : "";
        if (resolvedCloudName.isEmpty() || resolvedUploadPreset.isEmpty()) {
            postError(callback, "Cloudinary config is empty", null);
            return;
        }

        try {
            byte[] mediaBytes = readUriBytes(context, mediaUri);
            String mimeType = getMimeType(context.getContentResolver(), mediaUri);
            
            // Determine resource type (image or video)
            String resourceType = "image";
            if (mimeType != null && mimeType.startsWith("video/")) {
                resourceType = "video";
            }

            MediaType mediaType = MediaType.parse(mimeType != null ? mimeType : "image/*");
            RequestBody fileBody = RequestBody.create(mediaBytes, mediaType);
            String fileName = "upload." + getExtensionFromMime(mimeType);

            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, fileBody)
                    .addFormDataPart("upload_preset", resolvedUploadPreset)
                    .build();

            String endpoint = String.format(
                    Locale.US,
                    "https://api.cloudinary.com/v1_1/%s/%s/upload",
                    resolvedCloudName,
                    resourceType
            );

            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build();

            HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    postError(callback, "Upload failed: " + e.getMessage(), e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (ResponseBody body = response.body()) {
                        String bodyText = body != null ? body.string() : "";
                        if (!response.isSuccessful() || bodyText.isEmpty()) {
                            postError(callback, "Cloudinary response error: " + response.code() + " - " + bodyText, null);
                            return;
                        }

                        JSONObject json = new JSONObject(bodyText);
                        String secureUrl = json.optString("secure_url");
                        String publicId = json.optString("public_id");

                        if (secureUrl == null || secureUrl.trim().isEmpty()) {
                            postError(callback, "Cloudinary did not return secure_url", null);
                            return;
                        }

                        postSuccess(callback, secureUrl, publicId);
                    } catch (Exception e) {
                        postError(callback, "Failed to parse Cloudinary response", e);
                    }
                }
            });
        } catch (IOException e) {
            postError(callback, "Failed to read media from Uri", e);
        }
    }

    private static byte[] readUriBytes(Context context, Uri uri) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            if (inputStream == null) {
                throw new IOException("InputStream is null for Uri: " + uri);
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    private static String getMimeType(ContentResolver contentResolver, Uri uri) {
        String mimeType = contentResolver.getType(uri);
        return mimeType != null ? mimeType : "image/jpeg";
    }

    private static String getExtensionFromMime(String mimeType) {
        if (mimeType == null) {
            return "jpg";
        }
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return extension != null ? extension : "jpg";
    }

    private static void postSuccess(UploadCallback callback, String secureUrl, String publicId) {
        MAIN_HANDLER.post(() -> callback.onSuccess(secureUrl, publicId));
    }

    private static void postError(UploadCallback callback, String message, Throwable throwable) {
        MAIN_HANDLER.post(() -> callback.onError(message, throwable));
    }
}
