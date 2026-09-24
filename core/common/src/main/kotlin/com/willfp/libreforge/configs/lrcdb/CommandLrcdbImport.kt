package com.willfp.libreforge.configs.lrcdb

import com.willfp.eco.core.command.impl.Subcommand
import com.willfp.eco.core.config.ConfigType
import com.willfp.eco.core.config.readConfig
import com.willfp.libreforge.Plugins
import com.willfp.libreforge.configs.onLrcdbThread
import com.willfp.libreforge.plugin
import org.bukkit.command.CommandSender
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

internal object CommandLrcdbImport : Subcommand(
    plugin,
    "import",
    "libreforge.command.lrcdb",
    false
) {
    private data class DownloadResult(
        val success: Boolean,
        val code: Int,
        val message: String?,
        val pluginName: String? = null,
        val categoryID: String? = null,
        val name: String? = null,
        val contents: String? = null
    )

    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.langYml.getMessage("must-specify-lrcdb-id"))
            return
        }

        val id = args[0]

        onLrcdbThread(sender, {
            val url = URI("https://lrcdb.auxilor.io/api/v2/configs/$id?isDownload=true").toURL()
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                val code = connection.responseCode

                val isError = code in 400..599

                val text = (if (isError) connection.errorStream else connection.inputStream)
                    .bufferedReader()
                    .use { it.readText() }

                val res = readConfig(text, ConfigType.JSON)

                if (isError) {
                    DownloadResult(
                        false,
                        code,
                        res.getStringOrNull("message") ?: "HTTP Error $code"
                    )
                } else {
                    val config = res.getSubsection("config")
                    DownloadResult(
                        true,
                        code,
                        null,
                        config.getString("plugin"),
                        config.getString("category"),
                        config.getString("name"),
                        config.getString("contents")
                    )
                }
            } finally {
                connection.disconnect()
            }
        }) { replyTo, result ->
            val download = result.getOrElse { throwable ->
                replyTo.sendMessage(
                    plugin.langYml.getMessage("lrcdb-import-error")
                        .replace("%message%", throwable.message ?: throwable.javaClass.simpleName)
                )
                return@onLrcdbThread
            }

            if (!download.success) {
                replyTo.sendMessage(
                    plugin.langYml.getMessage("lrcdb-import-error")
                        .replace("%message%", download.message ?: "HTTP Error ${download.code}")
                )
                return@onLrcdbThread
            }

            val pluginName = download.pluginName ?: return@onLrcdbThread
            val categoryID = download.categoryID ?: return@onLrcdbThread
            val targetPlugin = Plugins[pluginName]
            if (targetPlugin == null) {
                replyTo.sendMessage(plugin.langYml.getMessage("plugin-not-installed"))
                return@onLrcdbThread
            }
            val category = targetPlugin.categories[categoryID]
            if (category == null) {
                replyTo.sendMessage(plugin.langYml.getMessage("invalid-category"))
                return@onLrcdbThread
            }

            val name = download.name ?: return@onLrcdbThread
            val contents = download.contents ?: return@onLrcdbThread
            val target = File(File(targetPlugin.dataFolder, "${category.directory}/imports"), "$name.yml")

            onLrcdbThread(replyTo, {
                target.parentFile.mkdirs()
                target.writeText(contents)
            }) { finalReplyTo, writeResult ->
                if (writeResult.isSuccess) {
                    finalReplyTo.sendMessage(
                    plugin.langYml.getMessage("lrcdb-import-success")
                        .replace("%name%", name)
                    )
                } else {
                    val throwable = writeResult.exceptionOrNull()
                    finalReplyTo.sendMessage(
                        plugin.langYml.getMessage("lrcdb-import-error")
                            .replace("%message%", throwable?.message ?: throwable?.javaClass?.simpleName ?: "IO error")
                    )
                }
            }
        }
    }
}
