package me.func.murder

import clepto.cristalix.Cristalix
import me.func.murder.donate.impl.NameTag
import me.func.murder.mod.ModHelper
import me.func.murder.mod.ModTransfer
import me.func.murder.user.Role
import org.bukkit.FireworkEffect
import org.bukkit.GameMode
import org.bukkit.entity.Firework
import org.bukkit.entity.Spider
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.cristalix.core.formatting.Formatting.fine
import ru.cristalix.core.realm.RealmStatus

enum class Status(val lastSecond: Int, val now: (Int, MurderGame) -> Int) {
    STARTING(30, { it, game ->
        // Если набор игроков начался, обновить статус реалма
        if (it == 40) game.status = RealmStatus.GAME_STARTED_CAN_JOIN

        val players = game.players

        // Обновление шкалы онлайна
        players.forEach {
            ModTransfer()
                .integer(game.slots)
                .integer(players.size)
                .boolean(true)
                .send("update-online", game.userManager.getUser(it.uniqueId))
        }
        var actualTime = it

        // Если время вышло и пора играть
        if (it / 20 == STARTING.lastSecond) {
            // Начать отсчет заново, так как мало игроков
            if (players.size + 3 < game.slots)
                actualTime = 1
            else {
                // Обновление статуса реалма, чтобы нельзя было войти
                game.status = RealmStatus.GAME_STARTED_RESTRICTED

                // Обнуление прошлого героя и добавления количества игр
                game.heroName = null

                // Создание каталок
                game.map.getLabels("gurney").map { game.gurney.create(it) }

                // Телепортация игроков на игровые точки и очистка инвентаря
                val places = game.map.getLabels("start")
                game.context.after(10) {
                    players.forEachIndexed { index, player ->
                        player.teleport(places[index])
                        player.inventory.clear()
                        player.itemOnCursor = null
                        player.openInventory.topInventory.clear()
                        val user = game.userManager.getUser(player)
                        user.stat.mask.setMask(user)
                    }
                }
                // Список игроков Murder
                val users = players.map { game.userManager.getUser(it) }
                // Выдача активных ролей
                val murder = users.maxByOrNull { it.stat.villagerStreak }!!
                murder.role = Role.MURDER
                murder.stat.villagerStreak = 0
                game.murderName = murder.player!!.name
                val detective = users.minus(murder).maxByOrNull { it.stat.villagerStreak }!!
                detective.role = Role.DETECTIVE
                detective.stat.villagerStreak = 0
                game.detectiveName = detective.player!!.name
                // Выдача мирных жителей
                users.forEach {
                    if (it.role != Role.MURDER && it.role != Role.DETECTIVE) {
                        it.role = Role.VILLAGER
                        it.stat.villagerStreak++
                    }
                }
                // Показать карту
                game.modHelper.loadMap(game.mapType)

                // Показ на экране роли и создание команд, чтобы игроки не видели чужие ники
                users.forEach { user ->
                    val player = user.player!!
                    val nameTag = user.stat.activeNameTag
                    player.playerListName =
                        if (nameTag == NameTag.NONE) " " else "${
                            nameTag.rare.colored
                        } §7${nameTag.title}" // todo
                    // me.func.murder.listener.tab.setTabView(player.uniqueId, me.func.murder.listener.tabView) todo tab
                    // me.func.murder.listener.tab.update(player)

                    ModHelper.sendTitle(user, "Роль: ${user.role.title}")

                    // Выполнение ролийных особенностей
                    game.context.after(10 * 20) {
                        user.role.start(user, game)
                    }
                    // Отправить информацию о начале игры клиенту
                    ModTransfer()
                        .string(user.role.shortTitle)
                        .send("murder-start", user)

                    game.modHelper.updateOnline()
                    // Сменить музыку
                    game.mapType.music.play(user)
                }
                // Заспавнить перевернутых пауков
                game.map.getLabels("spider").forEach {
                    val spider =
                        it.world.spawnEntity(it, org.bukkit.entity.EntityType.SPIDER) as Spider
                    spider.customName = "Grumm"
                    spider.isCustomNameVisible = false
                    spider.setMetadata("trash", org.bukkit.metadata.FixedMetadataValue(app, true))
                }

                game.activeStatus = GAME
                actualTime + 1
            }
        }
        // Если набралось максимальное количество игроков, то сократить время ожидания до 10 секунд
        if (players.size == game.slots && it / 20 < STARTING.lastSecond - 10)
            actualTime = (STARTING.lastSecond - 10) * 20
        actualTime
    }),
    GAME(330, { time, game ->
        // Обновление шкалы времени
        if (time % 20 == 0) {
            game.players.forEach {
                ModTransfer()
                    .integer(GAME.lastSecond)
                    .integer(time)
                    .boolean(false)
                    .send("update-online", game.userManager.getUser(it))
            }
        }
        // Каждые 10 секунд, генерировать золото в случайном месте
        if ((time / 20) % 10 == 0)
            game.goldManager.dropGoldRandomly()
        game.bowManager.rotateIfPresent(time)
        // Если осталось менее двух минут, выдать скорость мардеру,
        // и подсветить всех на 5 секунд, если меньше 30 сек. выдать свечение
        val glowing = PotionEffect(PotionEffectType.GLOWING, 100, 1, false, false)
        if (time == (GAME.lastSecond - 120) * 20) {
            game.players.forEach { player ->
                player.addPotionEffect(glowing)

                val user = game.userManager.getUser(player)

                ModHelper.sendTitle(user, "㥏 Скоро рассвет")
                if (user.role == Role.MURDER) {
                    player.walkSpeed = 0.25f
                    return@forEach
                }
            }
        } else if (time == (GAME.lastSecond - 30) * 20) {
            game.players
                .filter { it.gameMode != GameMode.SPECTATOR }
                .forEach { it.isGlowing = true }
        }
        // Проверка на победу
        if (game.winUtil.check4win()) {
            game.activeStatus = END
        }
        time
    }),
    END(340, { time, game ->
        if (GAME.lastSecond * 20 + 10 == time) {
            // Выдача побед выжившим и выдача всем доп. игр
            game.players.forEach {
                val user = game.userManager.getUser(it)
                if (it.gameMode != GameMode.SPECTATOR) {
                    user.stat.wins++
                    user.giveMoney(10)
                    if (Math.random() < 0.11) {
                        user.stat.lootbox++
                        game.broadcast(fine("§e${user.player!!.name} §fполучил §bлутбокс§f!"))
                    }
                    val firework = it.world!!.spawn(it.location, Firework::class.java)
                    val meta = firework.fireworkMeta
                    meta.addEffect(
                        FireworkEffect.builder()
                            .flicker(true)
                            .trail(true)
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .with(FireworkEffect.Type.BALL)
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(org.bukkit.Color.YELLOW)
                            .withColor(org.bukkit.Color.GREEN)
                            .withColor(org.bukkit.Color.WHITE)
                            .build()
                    )
                    meta.power = 0
                    firework.fireworkMeta = meta
                }
                user.stat.games++
                me.func.murder.util.Music.VILLAGER_WIN.play(user)
            }

            game.broadcast("")
            game.broadcast("§c§lКОНЕЦ! ${game.winMessage}")
            game.broadcast("    §cМаньяк ${game.murderName}")
            game.broadcast("    §bДетектив ${game.detectiveName}")
            if (game.heroName?.isNotEmpty() ?: false && game.detectiveName != game.heroName)
                game.broadcast("    §aГерой ${game.heroName}")
            game.broadcast("")
            game.context.after(20 * 8) {
                // Кик всех игроков с сервера
                Cristalix.transfer(
                    game.players.map { it.uniqueId },
                    MurderApp.LOBBY_SERVER
                )
            }
            // Очистка мусорных сущностей
            game.map.world.entities.filter { it.hasMetadata("trash") }.forEach { it.remove() }

            game.bowManager.clear()
        }
        when {
            time == GAME.lastSecond * 20 + 20 * 10 -> {
                game.isTerminated = true
                -1
            }
            time < (END.lastSecond - 10) * 20 -> (END.lastSecond - 10) * 20
            else -> time
        }
    });
}