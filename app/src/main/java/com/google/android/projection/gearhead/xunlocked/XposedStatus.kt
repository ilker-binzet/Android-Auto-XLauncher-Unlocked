package com.google.android.projection.gearhead.xunlocked

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks the connection to the Xposed framework service (libxposed/service) */
object XposedStatus {

    /** Snapshot of the framework state, `null` while the module is not active */
    data class Info(
        val frameworkName: String,
        val frameworkVersion: String,
        val apiVersion: Int,
        val androidAutoInScope: Boolean,
    )

    private val _info = MutableStateFlow<Info?>(null)
    val info: StateFlow<Info?> = _info.asStateFlow()

    @Volatile
    var service: XposedService? = null
        private set

    /** Register once per process, the framework binds as soon as the module app starts */
    fun init() {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                this@XposedStatus.service = service
                refresh()
            }

            override fun onServiceDied(service: XposedService) {
                this@XposedStatus.service = null
                _info.value = null
            }
        })
    }

    /** Re-read the framework state, e.g. after the scope changed */
    fun refresh() {
        val service = service ?: return
        _info.value = runCatching {
            Info(
                frameworkName = service.frameworkName,
                frameworkVersion = service.frameworkVersion,
                apiVersion = service.apiVersion,
                androidAutoInScope = RootShell.AA_PACKAGE in service.scope,
            )
        }.getOrNull()
    }

    /** Ask the framework to add Android Auto to the module scope */
    fun requestAndroidAutoScope(onResult: (Boolean, String?) -> Unit) {
        val service = service ?: return onResult(false, null)
        service.requestScope(listOf(RootShell.AA_PACKAGE), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                refresh()
                onResult(true, null)
            }

            override fun onScopeRequestFailed(message: String) {
                onResult(false, message)
            }
        })
    }
}
