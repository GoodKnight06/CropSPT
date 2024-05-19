package com.example.mycropspt
import android.Manifest

object Constants {
    const val TAG = "cameraX"
    const val FILE_NAME_FORMAT = "yyyyMMdd_HHmmss"
    const val REQUEST_CODE_PERMISSIONS = 123
    val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
}