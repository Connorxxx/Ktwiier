package com.connor.cronet.engine.internal

import android.content.Context
import com.connor.cronet.engine.CronetProviderStrategy
import org.chromium.net.CronetProvider

internal interface CronetProviderSelector {
    fun select(context: Context, strategy: CronetProviderStrategy): CronetProvider
}

internal object DefaultCronetProviderSelector : CronetProviderSelector {
    override fun select(context: Context, strategy: CronetProviderStrategy): CronetProvider {
        val providers = CronetProvider.getAllProviders(context)
            .filter { it.isEnabled }

        require(providers.isNotEmpty()) {
            "No enabled Cronet provider found"
        }

        val appPackaged = providers.firstOrNull {
            it.name == CronetProvider.PROVIDER_NAME_APP_PACKAGED
        }

        val fallback = providers.firstOrNull {
            it.name == CronetProvider.PROVIDER_NAME_FALLBACK
        }

        return when (strategy) {
            CronetProviderStrategy.AppPackagedOnly -> {
                requireNotNull(appPackaged) {
                    "App-packaged Cronet provider is required but unavailable"
                }
            }

            CronetProviderStrategy.PreferAppPackaged -> {
                appPackaged ?: chooseBestNonFallback(providers) ?: requireNotNull(fallback) {
                    "No Cronet provider available"
                }
            }

            CronetProviderStrategy.AutoBestAvailable -> {
                chooseBestNonFallback(providers) ?: requireNotNull(fallback) {
                    "No Cronet provider available"
                }
            }
        }
    }

    private fun chooseBestNonFallback(providers: List<CronetProvider>): CronetProvider? {
        return providers
            .asSequence()
            .filterNot { it.name == CronetProvider.PROVIDER_NAME_FALLBACK }
            .maxWithOrNull { left, right ->
                compareVersion(left.version, right.version)
            }
    }

    private fun versionKey(version: String): List<Int> {
        return version
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }
    }

    private fun compareVersion(left: String, right: String): Int {
        val leftParts = versionKey(left)
        val rightParts = versionKey(right)
        val max = maxOf(leftParts.size, rightParts.size)

        for (index in 0 until max) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }

            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }

        return 0
    }
}
