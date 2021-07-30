package me.func.commons.content

import clepto.bukkit.B
import dev.implario.bukkit.item.item
import me.func.commons.app
import me.func.commons.donate.DonateHelper
import me.func.commons.donate.DonatePosition
import me.func.commons.donate.MoneyFormatter
import me.func.commons.donate.impl.*
import me.func.commons.getByPlayer
import me.func.commons.user.User
import me.func.commons.worldMeta
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.cristalix.core.formatting.Formatting
import ru.cristalix.core.inventory.ClickableItem
import ru.cristalix.core.inventory.ControlledInventory
import ru.cristalix.core.inventory.InventoryContents
import ru.cristalix.core.inventory.InventoryProvider
import ru.cristalix.core.network.ISocketClient
import ru.cristalix.core.network.packages.MoneyTransactionRequestPackage
import ru.cristalix.core.network.packages.MoneyTransactionResponsePackage
import ru.cristalix.npcs.data.NpcBehaviour
import ru.cristalix.npcs.server.Npc
import ru.cristalix.npcs.server.Npcs
import java.util.function.Consumer

class CustomizationNPC {

    private val backItem = item {
        type = Material.CLAY_BALL
        text("§cНазад")
        nbt("other", "cancel")
    }.build()

    private val accessItem = item {
        text("§aКупить\n\n§7Это кнопка подтверждения\n§7покупки.")
        nbt("other", "access")
        enchant(Enchantment.LUCK, 1)
        type = Material.CLAY_BALL
    }.build()

    private val menu = ControlledInventory.builder()
        .title("MurderMystery")
        .rows(1)
        .columns(9)
        .provider(object : InventoryProvider {
            override fun init(player: Player, contents: InventoryContents) {
                contents.setLayout("XLKPCIXSX")

                val user = getByPlayer(player)
                val stat = user.stat

                contents.add('S', ClickableItem.empty(item {
                    type = Material.CLAY_BALL
                    nbt("other", "quest_week")
                    text("§bСтатистика\n\n§7Убийств: §c${stat.kills}\n§7Побед: §b${stat.wins}\n${MoneyFormatter.texted(stat.money)}\n§aСыграно ${stat.games} игр(ы)")
                }.build()))

                contents.add('P', ClickableItem.of(item {
                    type = Material.CLAY_BALL
                    nbt("other", "g2")
                    text("§bМогилы\n\n§7Выберите могилу, которая\n§7появится на месте\n§7вашей смерти.")
                }.build()) {
                    subInventory(player, 1) { _: Player, currentContent: InventoryContents ->
                        currentContent.setLayout("XIIIIIXBX")
                        pasteItems(user, false, currentContent, Corpse.values().filter { it != Corpse.NONE }) {
                            user.stat.activeCorpse = it as Corpse
                        }
                    }
                })
                contents.add('K', ClickableItem.of(item {
                    type = Material.CLAY_BALL
                    nbt("other", "guild_members_add")
                    text("§bЧастицы ходьбы\n\n§7Выберите тип частиц,\n§7которые будут появлять\n§7следом за вами.")
                }.build()) {
                    subInventory(player, 3) { _: Player, currentContent: InventoryContents ->
                        currentContent.setLayout(
                            "XIIIIIIIX",
                            "XIIIIIIIX",
                            "XXXXBXXXX"
                        )
                        pasteItems(user, false, currentContent, StepParticle.values().asIterable()) {
                            user.stat.activeParticle = it as StepParticle
                        }
                    }
                })
                contents.add('L', ClickableItem.of(item {
                    type = Material.CLAY_BALL
                    nbt("other", "new_booster_2")
                    text("§bПсевдонимы\n\n§7Выберите псевдоним,\n§7который появится в\n§7табе.")
                }.build()) {
                    subInventory(player, 3) { _: Player, currentContent: InventoryContents ->
                        currentContent.setLayout(
                            "XIIIIIIIX",
                            "XIIIIIIIX",
                            "XXXXBXXXX"
                        )
                        pasteItems(user, false, currentContent, NameTag.values().asIterable()) {
                            user.stat.activeNameTag = it as NameTag
                        }
                    }
                })
                contents.add('C', ClickableItem.of(item {
                    type = Material.CLAY_BALL
                    nbt("other", "new_lvl_rare_close")
                    text("§bМонеты\n\n§7Приобретите монеты,\n§7и ни в чем себе\n§7не отказывайте.")
                }.build()) {
                    subInventory(player, 1) { _: Player, currentContent: InventoryContents ->
                        currentContent.setLayout("XIIIIXXBX")
                        pasteItems(user, true, currentContent, MoneyKit.values().filter { it != MoneyKit.NONE }) {}
                    }
                })
                val chest = LootboxUnit.getIcon()
                contents.add('I', ClickableItem.of(chest) {
                    donateMenu(player, LootboxUnit, false)
                })
                contents.fillMask('X', ClickableItem.empty(ItemStack(Material.AIR)))
            }
        }).build()

    init {
        // Создание NPC
        val npcLabel = worldMeta.getLabel("guide")
        val npcArgs = npcLabel.tag.split(" ")
        npcLabel.setYaw(npcArgs[0].toFloat())
        npcLabel.setPitch(npcArgs[1].toFloat())
        Npcs.spawn(
            Npc.builder()
                .location(npcLabel)
                .name("§d§lMurder§f§lMystery")
                .behaviour(NpcBehaviour.STARE_AT_PLAYER)
                .skinUrl("https://webdata.c7x.dev/textures/skin/307264a1-2c69-11e8-b5ea-1cb72caa35fd")
                .skinDigest("307264a1-2c69-11e8-b5ea1cb72caa35fd")
                .type(EntityType.PLAYER)
                .onClick { it.performCommand("menu") }
                .build()
        )

        // Команда для открытия меню
        B.regCommand({ player, _ ->
            menu.open(player)
            null
        }, "menu", "help")
    }

    fun pasteItems(user: User, realMoney: Boolean, content: InventoryContents, item: Iterable<DonatePosition>, fill: (DonatePosition) -> Unit) {
        item.forEach { currentItem ->
            content.add('I', ClickableItem.of(DonateHelper.modifiedItem(user, currentItem)) {
                if (user.stat.donate.contains(currentItem)) {
                    fill(currentItem)
                    user.player!!.closeInventory()
                } else {
                    donateMenu(user.player!!, currentItem, realMoney)
                }
            })
        }
        content.add('B', ClickableItem.of(backItem) { user.player!!.performCommand("menu") })
    }

    fun subInventory(player: Player, rows: Int, inventory: (Player, InventoryContents) -> Any) {
        ControlledInventory.builder()
            .title("MurderMystery")
            .rows(rows)
            .columns(9)
            .provider(object : InventoryProvider {
                override fun init(player: Player, contents: InventoryContents) {
                    inventory(player, contents)
                    contents.fillMask('X', ClickableItem.empty(ItemStack(Material.AIR)))
                }
            }).build()
            .open(player)
    }

    private fun donateMenu(player: Player, donatePosition: DonatePosition, realMoney: Boolean) {
        subInventory(player, 1) { _, contents: InventoryContents ->
            contents.setLayout("XOXXXXGBX")
            contents.add('O', ClickableItem.empty(donatePosition.getIcon()))
            contents.add('G', ClickableItem.of(accessItem) {
                val user = getByPlayer(player)
                if (realMoney)
                    buy(user, donatePosition.getPrice(), donatePosition.getTitle()) { donatePosition.give(user) }
                else {
                    if (user.stat.donate.contains(donatePosition)) {
                        player.sendMessage(Formatting.error("У вас уже есть этот товар."))
                        player.closeInventory()
                    } else if (donatePosition.getPrice() > user.stat.money) {
                        player.sendMessage(Formatting.error("Не хватает денег :<"))
                        player.closeInventory()
                    } else {
                        user.minusMoney(donatePosition.getPrice())
                        donatePosition.give(user)
                        player.sendMessage(Formatting.fine("Успешно!"))
                        player.closeInventory()
                    }
                }
            })
            contents.add('B', ClickableItem.of(backItem) { player.performCommand("menu") })
        }
    }

    private fun buy(user: User, money: Int, desc: String, accept: Consumer<User>) {
        val player = user.player!!
        ISocketClient.get().writeAndAwaitResponse<MoneyTransactionResponsePackage>(
            MoneyTransactionRequestPackage(player.uniqueId, money, true, desc)
        ).thenAccept {
            if (it.errorMessage != null) {
                player.sendMessage(Formatting.error(it.errorMessage))
                return@thenAccept
            }
            if (!user.session.isActive) {
                player.sendMessage(Formatting.error("Что-то пошло не так... Попробуйте перезайти"))
                return@thenAccept
            }
            accept.accept(user)
            player.closeInventory()
            player.sendMessage(Formatting.fine("Спасибо за поддержку разработчиков!"))
        }
    }

}