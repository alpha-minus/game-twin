package com.twinlore.app.ui.screens.guide

import androidx.compose.runtime.Composable
import com.twinlore.app.ui.components.ImageZoomViewer

/**
 * Thin alias kept so existing guide call sites compile without churn. New code
 * should call [com.twinlore.app.ui.components.ImageZoomViewer] directly.
 */
@Composable
internal fun GuideImageZoomViewer(
    model: Any,
    onDismiss: () -> Unit
) = ImageZoomViewer(model = model, onDismiss = onDismiss)
