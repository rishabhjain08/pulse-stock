package com.pulsestock.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Provides Lifecycle / ViewModelStore / SavedStateRegistry for a ComposeView
 * that lives inside a Service (no Activity/Fragment lifecycle available).
 *
 * Usage:
 *   val owner = ServiceLifecycleOwner()
 *   owner.onCreate()
 *   composeView.setViewTreeLifecycleOwner(owner)
 *   composeView.setViewTreeViewModelStoreOwner(owner)
 *   composeView.setViewTreeSavedStateRegistryOwner(owner)
 *   // … add view to WindowManager …
 *   owner.onStart()
 *   owner.onResume()
 *   // When removing the view:
 *   owner.onDestroy()
 */
class ServiceLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner
{
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store             = ViewModelStore()
    private val controller        = SavedStateRegistryController.create(this)

    override val lifecycle:            Lifecycle          get() = lifecycleRegistry
    override val viewModelStore:       ViewModelStore      get() = store
    override val savedStateRegistry:   SavedStateRegistry  get() = controller.savedStateRegistry

    fun onCreate() {
        controller.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart()   = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    fun onResume()  = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onPause()   = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onStop()    = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
