package com.connor.kwitter.core.media

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.darwin.DISPATCH_QUEUE_PRIORITY_BACKGROUND
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue

@OptIn(ExperimentalForeignApi::class)
object VideoCache {

    private const val MAX_CACHE_SIZE = 2048L * 1024 * 1024 // 2GB
    private const val CACHE_DIR = "video_cache"

    private val fileManager = NSFileManager.defaultManager
    private val cacheDirectory: String by lazy {
        @Suppress("UNCHECKED_CAST")
        val paths = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true
        ) as List<String>
        val cachesDir = paths.first()
        val dir = "$cachesDir/$CACHE_DIR"
        if (!fileManager.fileExistsAtPath(dir)) {
            fileManager.createDirectoryAtPath(
                dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
        dir
    }

    fun cachedFileUrl(remoteUrl: String): NSURL? {
        val fileName = cacheKey(remoteUrl)
        val filePath = "$cacheDirectory/$fileName"
        return if (fileManager.fileExistsAtPath(filePath)) {
            NSURL.fileURLWithPath(filePath)
        } else null
    }

    fun cacheVideoAsync(remoteUrl: String) {
        if (cachedFileUrl(remoteUrl) != null) return

        val url = NSURL.URLWithString(remoteUrl) ?: return

        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_BACKGROUND.toLong(), 0u)) {
            val data = NSData.dataWithContentsOfURL(url) ?: return@dispatch_async
            val destPath = "$cacheDirectory/${cacheKey(remoteUrl)}"
            data.writeToFile(destPath, atomically = true)
            evictIfNeeded()
        }
    }

    private fun cacheKey(url: String): String {
        val hash = url.hashCode().toUInt().toString(16).padStart(8, '0')
        val path = url.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/')
        val ext = if ('.' in lastSegment) {
            lastSegment.substringAfterLast('.').take(4)
        } else {
            "mp4"
        }
        return "$hash.$ext"
    }

    private fun evictIfNeeded() {
        @Suppress("UNCHECKED_CAST")
        val contents = fileManager.contentsOfDirectoryAtPath(cacheDirectory, error = null)
                as? List<String> ?: return

        data class CachedFile(val path: String, val size: Long, val modDate: Double)

        val files = contents.mapNotNull { name ->
            val path = "$cacheDirectory/$name"
            @Suppress("UNCHECKED_CAST")
            val attrs = fileManager.attributesOfItemAtPath(path, error = null) ?: return@mapNotNull null
            val size = (attrs[NSFileSize] as NSNumber).longValue
            val modDate = (attrs[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970 ?: 0.0
            CachedFile(path, size, modDate)
        }

        val totalSize = files.sumOf { it.size }
        if (totalSize <= MAX_CACHE_SIZE) return

        val sorted = files.sortedBy { it.modDate }
        var remaining = totalSize
        for (file in sorted) {
            if (remaining <= MAX_CACHE_SIZE) break
            fileManager.removeItemAtPath(file.path, error = null)
            remaining -= file.size
        }
    }
}
