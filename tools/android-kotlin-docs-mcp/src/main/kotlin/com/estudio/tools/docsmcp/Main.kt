package com.estudio.tools.docsmcp

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 3000

    println("Starting android-kotlin-docs-mcp on http://127.0.0.1:$port/mcp")
    println("Allowed sources: developer.android.com, kotlinlang.org/docs, developer.android.com/jetpack/androidx/releases")

    embeddedServer(Netty, host = "127.0.0.1", port = port) {
        configureDocsMcpServer()
    }.start(wait = true)
}
