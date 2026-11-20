@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.example.photos.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Makes a thumbnail and the full-screen photo morph into each other when opening / closing
 * the viewer (the "container transform" you see on Samsung / iOS). No-op outside a transition host.
 */
@Composable
fun Modifier.mediaSharedBounds(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalAnimScope.current ?: return this
    val self = this
    return with(shared) {
        self.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "media-$key"),
            animatedVisibilityScope = anim,
        )
    }
}
