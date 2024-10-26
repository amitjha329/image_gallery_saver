package com.example.imagegallerysaver

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.webkit.MimeTypeMap
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ImageGallerySaverPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var methodChannel: MethodChannel
    private var applicationContext: Context? = null

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = binding.applicationContext
        methodChannel = MethodChannel(binding.binaryMessenger, "image_gallery_saver")
        methodChannel.setMethodCallHandler(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "saveImageToGallery" -> {
                val image = call.argument<ByteArray?>("imageBytes")
                val quality = call.argument<Int?>("quality") ?: 80 // Default to 80 if not provided
                val name = call.argument<String?>("name")

                val bitmap = image?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
                result.success(saveImageToGallery(bitmap, quality, name))
            }
            "saveFileToGallery" -> {
                val path = call.argument<String?>("file")
                val name = call.argument<String?>("name")
                result.success(saveFileToGallery(path, name))
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = null
        methodChannel.setMethodCallHandler(null)
    }

    private fun generateUri(extension: String = "", name: String? = null): Uri? {
        val fileName = name ?: System.currentTimeMillis().toString()
        val mimeType = getMIMEType(extension)
        val isVideo = mimeType?.startsWith("video") == true

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val contentUri = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = applicationContext?.contentResolver?.insert(contentUri, contentValues)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (uri != null) {
                    val outputStream = applicationContext?.contentResolver?.openOutputStream(uri)
                    outputStream?.close()
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    applicationContext?.contentResolver?.update(uri, contentValues, null, null)
                }
            }

            uri
        } else {
            val directory = Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, if (extension.isNotEmpty()) "$fileName.$extension" else fileName)
            Uri.fromFile(file)
        }
    }

    private fun getMIMEType(extension: String): String? {
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else {
            null
        }
    }

    private fun sendBroadcast(context: Context, fileUri: Uri?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = fileUri
            context.sendBroadcast(mediaScanIntent)
        }
    }

    private fun saveImageToGallery(
        bmp: Bitmap?,
        quality: Int,
        name: String?
    ): HashMap<String, Any?> {
        if (bmp == null) {
            return SaveResultModel(false, null, "Bitmap is null").toHashMap()
        }

        val context = applicationContext ?: return SaveResultModel(
            false,
            null,
            "Application context is null"
        ).toHashMap()

        return try {
            val fileUri = generateUri("jpg", name)
            if (fileUri != null) {
                val outputStream = context.contentResolver.openOutputStream(fileUri)
                outputStream?.use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, quality, it)
                    it.flush()
                }
                sendBroadcast(context, fileUri)
                SaveResultModel(true, fileUri.toString(), null).toHashMap()
            } else {
                SaveResultModel(false, null, "Failed to generate file URI").toHashMap()
            }
        } catch (e: IOException) {
            SaveResultModel(false, null, e.toString()).toHashMap()
        } finally {
            bmp.recycle()
        }
    }

    private fun saveFileToGallery(filePath: String?, name: String?): HashMap<String, Any?> {
        if (filePath == null) {
            return SaveResultModel(false, null, "File path is null").toHashMap()
        }

        val context = applicationContext ?: return SaveResultModel(
            false,
            null,
            "Application context is null"
        ).toHashMap()

        val originalFile = File(filePath)
        if (!originalFile.exists()) {
            return SaveResultModel(false, null, "File does not exist: $filePath").toHashMap()
        }

        return try {
            val fileUri = generateUri(originalFile.extension, name)
            if (fileUri != null) {
                val outputStream = context.contentResolver.openOutputStream(fileUri)
                outputStream?.use { output ->
                    FileInputStream(originalFile).use { input ->
                        val buffer = ByteArray(4 * 1024) // Adjust buffer size if needed
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }
                sendBroadcast(context, fileUri)
                SaveResultModel(true, fileUri.toString(), null).toHashMap()
            } else {
                SaveResultModel(false, null, "Failed to generate file URI").toHashMap()
            }
        } catch (e: IOException) {
            SaveResultModel(false, null, e.toString()).toHashMap()
        }
    }
}

data class SaveResultModel(
    val isSuccess: Boolean,
    val filePath: String? = null,
    val errorMessage: String? = null
) {
    fun toHashMap(): HashMap<String, Any?> {
        return hashMapOf(
            "isSuccess" to isSuccess,
            "filePath" to filePath,
            "errorMessage" to errorMessage
        )
    }
}