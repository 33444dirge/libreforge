package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.FoliaRunnableTask
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.plugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.ProjectileLaunchEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectAntigravityProjectile : Effect<NoCompileData>("antigravity_projectile") {
    override val description = "Makes all projectiles the player fires travel in a straight line, unaffected by gravity."
    override val categories = setOf("combat")
    private val players = ConcurrentHashMap<UUID, MutableList<UUID>>()

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        players.computeIfAbsent(dispatcher.uuid) { mutableListOf() }.add(identifiers.uuid)
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        players[dispatcher.uuid]?.remove(identifiers.uuid)
    }

    @EventHandler
    fun handle(event: ProjectileLaunchEvent) {
        val player = event.entity.shooter as? Player ?: return
        if (players[player.uniqueId]?.isEmpty() != false) return
        val projectile = event.entity
        projectile.setGravity(false)
        val launchSpeed = projectile.velocity.length()
        var task: FoliaRunnableTask? = null
        task = FoliaRunnableTask(plugin, Runnable {
            if (projectile.isDead || projectile.isOnGround) {
                task?.cancel()
                return@Runnable
            }
            val velocity = projectile.velocity
            val nextChunk = projectile.location.add(velocity).chunk
            if (!nextChunk.isLoaded) {
                projectile.setGravity(true)
                task?.cancel()
                return@Runnable
            }
            val currentSpeed = velocity.length()
            if (currentSpeed > 0) {
                projectile.velocity = velocity.multiply(launchSpeed / currentSpeed)
            }
        })

        task.runTask(projectile, 0L, 1L)
    }
}
