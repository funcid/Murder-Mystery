package me.func.murder.dbd.util

import me.func.battlepass.BattlePassUtil
import me.func.battlepass.quest.QuestType
import me.func.murder.MurderGame
import me.func.murder.getUser
import me.func.murder.user.Role

/**
 * DBD
 */
class DbdWinUtil(private val game: MurderGame) {
    var winMessage = "§eЧто-то пошло не так..."

    fun check4win(): Boolean {
        val alive = game.players.map { game.userManager.getUser(it) }.filter { it.role == Role.VICTIM }
        val out = game.players.map { game.userManager.getUser(it) }.filter { it.out }

        return when {
            game.players.isEmpty() -> true
            alive.isEmpty() && out.isEmpty() && (game.killer?.role ?: false) == Role.MURDER -> {
                BattlePassUtil.update(game.killer?.player!!, QuestType.WIN, 1, false)
                game.killer!!.stat.eventWins++
                winMessage = "§cМаньяк убил все живое..."
                true
            }
            (alive.isNotEmpty() || out.isNotEmpty()) && (game.killer == null || !game.killer!!.player!!.isOnline) -> {
                winMessage = "§dМаньяк вышел из игры..."
                true
            }
            alive.isEmpty() && out.isNotEmpty() -> {
                out.forEach {
                    it.giveMoney(5)
                    BattlePassUtil.update(it.player!!, QuestType.WIN, 1, false)
                    it.stat.eventWins++
                }
                winMessage = "§aСпаслись: §f§l${out.joinToString { it.player!!.name }}"
                true
            }
            game.activeStatus.lastSecond * 20 == game.dbdTimer!!.time && alive.isEmpty() && out.isEmpty() -> {
                game.killer!!.stat.eventWins++
                BattlePassUtil.update(game.killer?.player!!, QuestType.WIN, 1, false)
                winMessage = "§cВремя вышло... Людей внутри больше не видели."
                true
            }
            game.activeStatus.lastSecond * 20 == game.dbdTimer!!.time && out.isNotEmpty() -> {
                out.forEach {
                    it.giveMoney(7)
                    BattlePassUtil.update(it.player!!, QuestType.WIN, 1, false)
                    it.stat.eventWins++
                }
                winMessage = "§aЛюди спаслись, потерпевшие: §f§l${out.joinToString { it.player!!.name }}"
                true
            }
            game.activeStatus.lastSecond * 20 == game.dbdTimer!!.time -> {
                BattlePassUtil.update(game.killer?.player!!, QuestType.WIN, 1, false)
                game.killer!!.stat.eventWins++
                winMessage = "§cМаньяк убил все живое..."
                true
            }
            else -> false
        }
    }

}