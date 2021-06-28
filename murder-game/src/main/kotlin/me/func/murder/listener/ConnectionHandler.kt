package me.func.murder.listener

import me.func.murder.Status
import me.func.murder.activeBar
import me.func.murder.activeStatus
import me.func.murder.app
import clepto.bukkit.B
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import net.minecraft.server.v1_12_R1.PacketDataSerializer
import net.minecraft.server.v1_12_R1.PacketPlayOutCustomPayload
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.cristalix.core.display.DisplayChannels
import ru.cristalix.core.display.messages.Mod
import java.io.File
import java.nio.file.Files
import java.util.*

class ConnectionHandler : Listener {

    // Прогрузка файлов модов
    private var modList = try {
        File("./mods/").listFiles()!!
            .filter { it.name.contains("bundle") }
            .map {
                val buffer = Unpooled.buffer()
                buffer.writeBytes(Mod.serialize(Mod(Files.readAllBytes(it.toPath()))))
                buffer
            }.toList()
    } catch (exception: Exception) {
        Collections.emptyList()
    }

    // Получении точки спавна
    private val spawn = app.worldMeta.getLabel("spawn").toCenterLocation()

    @EventHandler
    fun PlayerJoinEvent.handle() {
        player.inventory.clear()
        player.gameMode = GameMode.ADVENTURE

        val user = app.getUser(player)

        B.postpone(10) {
            player.teleport(spawn)

            player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent("§eШансы: §cМаньяк - ${2 * (1 + user.stat.villagerStreak)}%§e, §bДетектив - ${3 * (1 + user.stat.villagerStreak)}%")
            )

            modList.forEach {
                user.sendPacket(
                    PacketPlayOutCustomPayload(
                        DisplayChannels.MOD_CHANNEL,
                        PacketDataSerializer(it.retainedSlice())
                    )
                )
            }
        }

        if (activeStatus == Status.STARTING) {
            activeBar.addViewer(player.uniqueId)
        }
    }

    @EventHandler
    fun PlayerQuitEvent.handle() {
        activeBar.removeViewer(player.uniqueId)
    }
}