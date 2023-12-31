package com.example.cameraxandjetpackcompose

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File
import java.util.Calendar
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preview = androidx.camera.core.Preview.Builder().build()
        val imageCapture = ImageCapture.Builder()
            .build()
        setContent {
            val cameraPermissionState= rememberMultiplePermissionsState(
                permissions = listOf(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )

            if(!cameraPermissionState.allPermissionsGranted) {
                Column {
                    Button(onClick = { cameraPermissionState.launchMultiplePermissionRequest() }) {
                        Text(text = "Ask for permissions")
                    }
                }
            }

            if(cameraPermissionState.allPermissionsGranted)
                CameraView(imageCapture = imageCapture, preview = preview)

        }
    }
    @Composable
    fun CameraView(imageCapture: ImageCapture, preview: androidx.camera.core.Preview) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current

        val previewView = remember {
            PreviewView(context)
        }
        var uri by remember {
            mutableStateOf<Uri?>(null)
        }

        var lensFacing by rememberSaveable {
            mutableStateOf(CameraSelector.LENS_FACING_BACK)
        }

        val outputDir = File(context.getExternalFilesDir(null)?.path)


        //Init camera
        LaunchedEffect(key1 = lensFacing) {
            val cameraProvider = ProcessCameraProvider.getInstance(context).await()
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(imageCapture)
                .addUseCase(preview)
                .apply { previewView.viewPort?.let { setViewPort(it) } }
                .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycle, cameraSelector, useCaseGroup)

            preview.setSurfaceProvider(previewView.surfaceProvider)
        }

        Box(
            modifier = Modifier
            .fillMaxSize(),
            contentAlignment = Alignment.Center) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Column{
                Button(onClick = {
                    takePhoto(
                        imageCapture = imageCapture,
                        outputDir = outputDir,
                    onImageCaptured = {result ->
                        uri = result
                    },
                    onError = {
                        Log.d("TAG","Error camera!!!")
                    }
                    )
                }) {
                    Text(text = "Take photo")
                }
                Button(onClick = { val lens =
                    if(lensFacing == CameraSelector.LENS_FACING_FRONT) LENS_FACING_BACK
                    else CameraSelector.LENS_FACING_FRONT
                    lensFacing = lens
                }) {
                    Text(text = "Change lens facing")
                }

                if (uri != null) {
                    AsyncImage(
                        modifier = Modifier.wrapContentSize(),
                        model = uri,
                        contentDescription = null
                    )
                }
            }
        }

    }

    fun takePhoto(
        imageCapture: ImageCapture,
        outputDir: File,
        onImageCaptured: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val executor =  Executors.newSingleThreadExecutor()
        val file = File(outputDir,"${Calendar.getInstance().timeInMillis.toString()}.jpg")
        val outputOpts = ImageCapture.OutputFileOptions.Builder(file).build()
        val callback = object : ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
               outputFileResults.savedUri?.let(onImageCaptured)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }

        }
        imageCapture.takePicture(outputOpts,executor, callback)
    }
}


