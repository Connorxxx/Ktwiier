package com.connor.cronet.engine.internal.request

import java.util.concurrent.atomic.AtomicBoolean

internal class SingleTerminalLatch {
    private val terminal = AtomicBoolean(false)

    fun tryEnterTerminal(): Boolean = terminal.compareAndSet(false, true)

    val isTerminal: Boolean
        get() = terminal.get()
}
