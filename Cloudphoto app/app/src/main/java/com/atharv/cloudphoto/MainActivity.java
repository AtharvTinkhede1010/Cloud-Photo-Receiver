package com.atharv.cloudphoto;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_VIDEO_CAPTURE = 2;
    private static final int REQUEST_PERMISSIONS = 100;

    private TextView statusText, txtServerStatus;
    private EditText editServerUrl;
    private Uri fileUri;
    private final OkHttpClient client = new OkHttpClient();
    private SharedPreferences prefs;
    private File tempDir;
    private File capturedFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button photoButton = findViewById(R.id.btnTakePhoto);
        Button videoButton = findViewById(R.id.btnRecordVideo);
        Button btnTestServer = findViewById(R.id.btnTestServer);
        editServerUrl = findViewById(R.id.editServerUrl);
        txtServerStatus = findViewById(R.id.txtServerStatus);
        statusText = findViewById(R.id.statusText);

        prefs = getSharedPreferences("CloudPhotoPrefs", MODE_PRIVATE);

        // Create temporary folder
        tempDir = new File(getExternalFilesDir(null), "temp_uploads");

        if (!tempDir.exists()) tempDir.mkdirs();
        Log.d("UPLOAD_DEBUG", "Captured file path:"+tempDir.getAbsolutePath());
        // Clean old files
        cleanOldTempFiles();

        // Load saved server URL or use blank default
        String savedUrl = prefs.getString("server_url", "");
        if (savedUrl.isEmpty()) {
            savedUrl = "http://192.168.1.7:8000/upload"; // optional fallback for first-time users
        }

        Log.d("UPLOAD_DEBUG", "Uploading to: " + savedUrl);

        editServerUrl.setText(savedUrl);

        // Request necessary permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        }

        // Take Photo
        photoButton.setOnClickListener(v -> {
            try {
                capturedFile = new File(tempDir, "photo_" + System.currentTimeMillis() + ".jpg");
                fileUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        capturedFile
                );

                // 💾 Save the file path in case the activity is recreated
                prefs.edit().putString("last_capture_path", capturedFile.getAbsolutePath()).apply();

                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });



        // Record Video
        videoButton.setOnClickListener(v -> {
            try {
                capturedFile = new File(tempDir, "video_" + System.currentTimeMillis() + ".mp4");
                fileUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".provider",
                        capturedFile);


                Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Log.d("UPLOAD_DEBUG", "About to launch camera. File path = " + capturedFile.getAbsolutePath());
                Log.d("UPLOAD_DEBUG", "File exists before capture: " + capturedFile.exists());
                Log.d("UPLOAD_DEBUG", "File URI: " + fileUri);
                startActivityForResult(intent, REQUEST_VIDEO_CAPTURE);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Video capture error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Test Server connection
        btnTestServer.setOnClickListener(v -> {
            String urlToTest = editServerUrl.getText().toString().trim();
            if (!urlToTest.startsWith("http")) {
                txtServerStatus.setText("⚠️ Invalid URL format");
                return;
            }

            prefs.edit().putString("server_url", urlToTest).apply();
            txtServerStatus.setText("⏳ Checking...");

            new Thread(() -> {
                try {
                    // just test the base or /upload path
                    URL url = new URL(urlToTest);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.connect();
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    runOnUiThread(() -> txtServerStatus.setText("🟢 Active (" + code + ")"));
                } catch (Exception e) {
                    runOnUiThread(() -> txtServerStatus.setText("🔴 Inactive — check Wi-Fi/IP"));
                }
            }).start();
        });
    }

    // Delete old temp files
    private void cleanOldTempFiles() {
        long cutoff = System.currentTimeMillis() - (60 * 60 * 1000);
        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.lastModified() < cutoff) file.delete();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (capturedFile == null) {
                // 🔁 Restore if lost
                String lastPath = prefs.getString("last_capture_path", null);
                if (lastPath != null) {
                    capturedFile = new File(lastPath);
                }
            }

            if (capturedFile != null && capturedFile.exists()) {
                Log.d("UPLOAD_DEBUG", "After capture: file = " + capturedFile.getAbsolutePath());
                Log.d("UPLOAD_DEBUG", "File length = " + capturedFile.length());
                statusText.setText("📤 Uploading...");
                uploadFile(capturedFile);
            } else {
                statusText.setText("❌ File missing after capture");
            }
        } else {
            Toast.makeText(this, "Action canceled", Toast.LENGTH_SHORT).show();
        }
    }



    private void uploadFile(File file) {
        new Thread(() -> {
            try {
                String serverUrl = editServerUrl.getText().toString().trim();
                if (!serverUrl.startsWith("http")) {
                    runOnUiThread(() -> statusText.setText("⚠️ Invalid server URL"));
                    return;
                }

                RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
                MultipartBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.getName(), fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url(serverUrl)
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    file.delete();
                    runOnUiThread(() -> statusText.setText("✅ Upload successful!"));
                } else {
                    showUploadFailedDialog(file);
                }
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
                showUploadFailedDialog(file);
            }
        }).start();
    }

    private void showUploadFailedDialog(File file) {
        runOnUiThread(() -> {
            statusText.setText("❌ Upload failed. What do you want to do?");
            new AlertDialog.Builder(this)
                    .setTitle("Upload Failed")
                    .setMessage("Would you like to retry or save the file?")
                    .setPositiveButton("Retry", (dialog, which) -> uploadFile(file))
                    .setNegativeButton("Save to Gallery", (dialog, which) -> saveToGallery(file))
                    .setNeutralButton("Cancel", null)
                    .show();
        });
    }

    private void saveToGallery(File file) {
        try {
            String mimeType = file.getName().endsWith(".mp4") ? "video/mp4" : "image/jpeg";

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    file.getName().endsWith(".mp4") ? "DCIM/CloudVideo" : "DCIM/CloudPhoto");

            Uri uri = getContentResolver().insert(
                    file.getName().endsWith(".mp4")
                            ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );

            if (uri != null) {
                InputStream input = getContentResolver().openInputStream(Uri.fromFile(file));
                if (input == null) return;
                FileOutputStream output = (FileOutputStream) getContentResolver().openOutputStream(uri);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                input.close();
                output.close();
                Toast.makeText(this, "✅ Saved to Gallery", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "⚠️ Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
