package com.example.customcameraapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.net.URI;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private int REQUEST_CODE_PERMISSIONS = 101;
    private String[] REQUEST_PERMISSIONS = new String[]{"android.permission.CAMERA","android.permission.WRITE_EXTERNAL_STORAGE"};

    TextureView textureView;
    String imageNamge = "Image";
    ProgressDialog progressDialog;

    private FirebaseStorage storage;
    private StorageReference storageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().hide();

        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        textureView = (TextureView) findViewById(R.id.view_finder);

        if(allPermissionGranted()){
            
            startCamera();
        }
        else{
            ActivityCompat.requestPermissions(this,REQUEST_PERMISSIONS,REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startCamera() {

        CameraX.unbindAll();

        Rational aspectRation = new Rational(textureView.getWidth(),textureView.getHeight());
        Size screen = new Size(textureView.getWidth(),textureView.getHeight());

        PreviewConfig pConfig = new PreviewConfig.Builder().setTargetAspectRatio(aspectRation).setTargetResolution(screen).build();
        Preview preview = new Preview(pConfig);

        preview.setOnPreviewOutputUpdateListener(
                new Preview.OnPreviewOutputUpdateListener() {
                    @Override
                    public void onUpdated(Preview.PreviewOutput output) {

                        ViewGroup parent = (ViewGroup)  textureView.getParent();
                        parent.removeView(textureView);
                        parent.addView(textureView);

                        textureView.setSurfaceTexture(output.getSurfaceTexture());
                        updateTransform();

                    }
                }
        );


        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder().setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY).
                setTargetRotation(getWindowManager().getDefaultDisplay().getRotation()).build();
        final ImageCapture imgCap = new ImageCapture(imageCaptureConfig);

        findViewById(R.id.imageCapture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

//                takePhoto();
                imageNamge += System.currentTimeMillis() + ".jpg";
                File file = new File(Environment.getDataDirectory()+"/data/com.example.customcameraapp/"+imageNamge);
//                File xfile = new File(Environment.getDataDirectory()+"/data/"+System.currentTimeMillis());
                imgCap.takePicture(file, new ImageCapture.OnImageSavedListener() {
                    @Override
                    public void onImageSaved(@NonNull File file) {
                        String msg = "image Captured at" + file.getAbsolutePath();

                        Toast.makeText(getBaseContext(), msg,Toast.LENGTH_LONG).show();

                        File folder = new File(Environment.getDataDirectory()+"/data/com.example.customcameraapp/"+imageNamge);
                        uploadImage(Uri.fromFile(folder));

//                        if(listOfFolders!=null) {
//                            for (File image : listOfFolders) {
//
//                                if (image.isFile()) {
//                                    File x = new File(Environment.getDataDirectory()+"/data/com.example.customcameraapp/1599405645264.jpg");
//                                    uploadImage(Uri.fromFile(x));
//                                }
//                            }
//                        }else {
//                            Toast.makeText(getBaseContext(),"no files to upload",Toast.LENGTH_SHORT).show();
//                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCapture.UseCaseError useCaseError, @NonNull String message, @Nullable Throwable cause) {

                        String msg = "image Capture failed" + message;
                        Toast.makeText(getBaseContext(),msg,Toast.LENGTH_LONG).show();
                        if(cause!=null){
                            cause.printStackTrace();
                        }

                    }
                });
            }
        });

        CameraX.bindToLifecycle(this,preview,imgCap );

    }

//    private void takePhoto() {
//
//        File photoFile = new File()
//    }

    private void uploadImage(android.net.Uri file) {


        startLoadingUI();
        StorageReference riversRef = storageReference.child("images/"+imageNamge);

        riversRef.putFile(file)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Get a URL to the uploaded content
                        progressDialog.dismiss();
                        Snackbar.make(findViewById(R.id.view_finder),"Image Uploaded", Snackbar.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // Handle unsuccessful uploads
                        progressDialog.dismiss(); 
                        Toast.makeText(getBaseContext(),"Failed",Toast.LENGTH_SHORT).show();
                        // ...
                    }
                });
    }

    private void startLoadingUI() {

        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.show();
        progressDialog.setContentView(R.layout.progress_dialog);
        progressDialog.getWindow().setBackgroundDrawableResource(
                android.R.color.transparent
        );
    }

    private void updateTransform() {

        Matrix mx = new Matrix();
        float w = textureView.getMeasuredWidth();
        float h = textureView.getMeasuredHeight();

        float cX = w / 2f;
        float cY = h / 2f;

        int rotationalDgr;
        int rotation = (int) textureView.getRotation();

        switch (rotation){
            case Surface.ROTATION_0:
                rotationalDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationalDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationalDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationalDgr = 270;
                break;
            default:
                return;

        }

        mx.postRotate((float) rotationalDgr, cX, cY);
        textureView.setTransform(mx);


    }

    private boolean allPermissionGranted() {

        for(String permission : REQUEST_PERMISSIONS){

            if(ContextCompat.checkSelfPermission(this,permission) != PackageManager.PERMISSION_GRANTED){

                return  false;
            }
        }
        return true;
    }
}
