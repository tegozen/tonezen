package com.tonezen.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import kotlin.math.max

internal fun cachePickedAvatarUri(context: Context, uri: Uri): Uri? {
    val dest = File.createTempFile("avatar-pick-", ".img", context.cacheDir)
    return try {
        if (copyUriToFile(context, uri, dest)) {
            Uri.fromFile(dest)
        } else {
            dest.delete()
            null
        }
    } catch (_: Exception) {
        dest.delete()
        null
    }
}

internal fun deleteCachedAvatarUri(uri: Uri) {
    if (uri.scheme.equals("file", ignoreCase = true)) {
        uri.path?.let { File(it).delete() }
    }
}

internal fun loadOrientedAvatarBitmap(
    context: Context,
    uri: Uri,
    maxSidePx: Int = 2048,
): Bitmap? {
    val decoded = decodeBitmap(context, uri, maxSidePx) ?: return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return decoded
    }
    val orientation = readExifOrientation(context, uri)
    return applyExifOrientation(decoded, orientation)
}

private fun decodeBitmap(context: Context, uri: Uri, maxSidePx: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(context, uri, maxSidePx)?.let { return it }
    }
    return decodeWithBitmapFactory(context, uri, maxSidePx)
}

private fun decodeWithImageDecoder(context: Context, uri: Uri, maxSidePx: Int): Bitmap? {
    return try {
        val source = if (isLocalFileUri(uri)) {
            ImageDecoder.createSource(File(requireNotNull(uri.path)))
        } else {
            ImageDecoder.createSource(context.contentResolver, uri)
        }
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val sampleSize = calculateInSampleSize(
                width = info.size.width,
                height = info.size.height,
                maxSidePx = maxSidePx,
            )
            if (sampleSize > 1) {
                decoder.setTargetSize(
                    (info.size.width / sampleSize).coerceAtLeast(1),
                    (info.size.height / sampleSize).coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } catch (_: Exception) {
        null
    }
}

private fun decodeWithBitmapFactory(context: Context, uri: Uri, maxSidePx: Int): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (!decodeBounds(context, uri, boundsOptions)) return null
    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

    val sampleSize = calculateInSampleSize(
        width = boundsOptions.outWidth,
        height = boundsOptions.outHeight,
        maxSidePx = maxSidePx,
    )
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return when {
        isLocalFileUri(uri) -> BitmapFactory.decodeFile(uri.path, decodeOptions)
        else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, decodeOptions)
        } ?: context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }
}

private fun decodeBounds(context: Context, uri: Uri, options: BitmapFactory.Options): Boolean {
    if (isLocalFileUri(uri)) {
        BitmapFactory.decodeFile(uri.path, options)
        return options.outWidth > 0 && options.outHeight > 0
    }
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
        return options.outWidth > 0 || options.outHeight > 0
    }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
        return options.outWidth > 0 || options.outHeight > 0
    }
    return false
}

private fun readExifOrientation(context: Context, uri: Uri): Int {
    return try {
        when {
            isLocalFileUri(uri) -> ExifInterface(requireNotNull(uri.path)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                ExifInterface(pfd.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
}

private fun copyUriToFile(context: Context, uri: Uri, dest: File): Boolean {
    context.contentResolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
        return dest.length() > 0L
    }
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        FileInputStream(pfd.fileDescriptor).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest.length() > 0L
    }
    return false
}

private fun isLocalFileUri(uri: Uri): Boolean =
    uri.scheme.equals("file", ignoreCase = true) && !uri.path.isNullOrBlank()

private fun calculateInSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
    var sampleSize = 1
    val longestSide = max(width, height)
    while (longestSide / sampleSize > maxSidePx) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) {
        bitmap.recycle()
    }
    return rotated
}
