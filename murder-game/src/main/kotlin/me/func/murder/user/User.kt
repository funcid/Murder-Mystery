package me.func.murder.user

import dev.implario.kensuke.KensukeSession
import dev.implario.kensuke.impl.bukkit.IBukkitKensukeUser
import me.func.donate.impl.*
import me.func.murder.map.MapType
import me.func.murder.mod.ModHelper
import me.func.murder.mod.ModTransfer
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.minecraft.server.v1_12_R1.Packet
import net.minecraft.server.v1_12_R1.PlayerConnection
import org.bukkit.Location
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer
import org.bukkit.entity.Player
import java.util.UUID

class User(session: KensukeSession, stat: Stat?) : IBukkitKensukeUser {

    private var connection: PlayerConnection? = null
    var tempLocation: Location? = null
    var bites = 0
    var lightTicks = 260
    var countdown = 0
    var out = false
    var hearts = 2
    var fuel = 0
    var role = Role.NONE
    var animationLock = false

    var stat: Stat
    private var player: Player? = null

    override fun setPlayer(p: Player) {
        player = p
    }

    override fun getPlayer(): Player? {
        return player
    }

    private var session: KensukeSession

    override fun getSession(): KensukeSession {
        return session
    }

    fun giveMoney(money: Int) {
        changeMoney(money)
    }

    fun minusMoney(money: Int) {
        changeMoney(-money)
    }

    fun sendPlayAgain(prefix: String, map: MapType) { // todo
        player!!.spigot().sendMessage(
            *ComponentBuilder("\n$prefix §fИграть на Cristalix §dMurderMystery §e§lКЛИК\n").event(
                ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/next MUR"
                )
            ).create()
        )
    }

    private fun changeMoney(money: Int) {
        stat.money += money
        ModTransfer().integer(money).send("murder:money", this)
        ModHelper.updateBalance(this)
    }

    init {
        if (stat == null) {
            this.stat = Stat(
                UUID.fromString(session.userId), 0, 0, 0, 0, 0, 0, 2, true, 1,
                0,
                0,
                0,
                0,
                0,
                0,
                "",
            )
        } else {
            if (stat.music == null) stat.music = true // todo wtf
            if (stat.lootboxOpenned == null) stat.lootboxOpenned = 0
            if (stat.moneyBooster == null) stat.moneyBooster = 1
            this.stat = stat
        }
        this.session = session
    }

    fun sendPacket(packet: Packet<*>) {
        if (player == null) return
        if (connection == null) connection = (player as CraftPlayer).handle.playerConnection
        connection?.sendPacket(packet)
    }
}