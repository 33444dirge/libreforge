package com.willfp.libreforge.configs

import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.config
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.config.readConfig
import com.willfp.eco.core.registry.Registrable
import com.willfp.libreforge.SchedulerHelper
import com.willfp.libreforge.plugin
import org.bukkit.command.CommandSender
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.Future

private val executor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "libreforge-lrcdb").apply { isDaemon = true }
}

internal fun <T> onLrcdbThread(action: () -> T): Future<T> = executor.submit(action)

internal fun <T> onLrcdbThread(
    sender: CommandSender,
    action: () -> T,
    complete: (CommandSender, Result<T>) -> Unit
) {
    val future = onLrcdbThread(action)

    fun pollFromOwner() {
        SchedulerHelper.runTaskLater(plugin, sender, Runnable {
            if (future.isDone) {
                complete(sender, runCatching { future.get() })
            } else {
                pollFromOwner()
            }
        }, 1)
    }

    pollFromOwner()
}

internal fun shutdownLrcdbThread() {
    executor.shutdownNow()
}

private val client = HttpClient.newBuilder().build()

internal data class PreparedExport(
    val request: HttpRequest?,
    val immediateResponse: ExportResponse?
)

data class LibreforgeObjectConfig(
    val config: Config,
    val contents: String,
    val name: String,
    val category: LibreforgeConfigCategory
) : Registrable {
    internal fun prepareShare(private: Boolean): PreparedExport {
        if (!category.supportsSharing) {
            return PreparedExport(
                null,
                ExportResponse(
                    false,
                    400,
                    config {
                        "message" to "Configs in this category cannot be shared"
                    }
                )
            )
        }

        val body = config(ConfigType.JSON) {
            "name" to name
            "plugin" to category.plugin.name
            "category" to category.id
            "author" to plugin.configYml.getString("lrcdb.author")
            "apiKey" to plugin.configYml.getString("lrcdb.key")
            "contents" to contents
            "isPrivate" to private
        }.toPlaintext()

        return PreparedExport(
            HttpRequest.newBuilder()
            .uri(URI.create("https://lrcdb.auxilor.io/api/v2/configs"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
            null
        )
    }

    internal fun executeShare(prepared: PreparedExport): ExportResponse {
        prepared.immediateResponse?.let { return it }
        val request = prepared.request ?: error("Prepared export has no request or response")

        val res = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            return ExportResponse(
                false,
                400,
                config {
                    "message" to e.message
                }
            )
        }

        val code = res.statusCode()

        val isError = code in 400..599
        val json = readConfig(res.body(), ConfigType.JSON)

        return ExportResponse(
            !isError,
            code,
            json
        )
    }

    fun share(private: Boolean): ExportResponse = executeShare(prepareShare(private))

    override fun onRegister() {
        if (!plugin.configYml.getBool("lrcdb.share-configs.enabled")) {
            return
        }

        val prepared = prepareShare(!plugin.configYml.getBool("lrcdb.share-configs.publicly"))
        onLrcdbThread { executeShare(prepared) }
    }

    override fun getID(): String {
        return name
    }
}
