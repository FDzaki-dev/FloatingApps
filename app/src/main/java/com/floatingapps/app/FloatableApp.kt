package com.floatingapps.app

import android.graphics.drawable.Drawable

data class FloatableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable
)
