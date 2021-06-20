package me.func.murder

import clepto.bukkit.B
import clepto.cristalix.WorldMeta
import dev.implario.bukkit.platform.Platforms
import dev.implario.kensuke.Scope
import dev.implario.kensuke.Session
import dev.implario.kensuke.impl.bukkit.BukkitKensuke
import dev.implario.kensuke.impl.bukkit.BukkitUserManager
import dev.implario.platform.impl.darkpaper.PlatformDarkPaper
import me.func.murder.bar.GameBar
import me.func.murder.listener.GoldListener
import me.func.murder.listener.ConnectionHandler
import me.func.murder.listener.DamageListener
import me.func.murder.listener.GlobalListeners
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.cristalix.core.CoreApi
import ru.cristalix.core.inventory.IInventoryService
import ru.cristalix.core.inventory.InventoryService
import ru.cristalix.core.network.ISocketClient
import ru.cristalix.core.party.IPartyService
import ru.cristalix.core.party.PartyService
import ru.cristalix.core.realm.IRealmService
import ru.cristalix.core.realm.RealmStatus
import ru.cristalix.core.transfer.ITransferService
import ru.cristalix.core.transfer.TransferService
import me.func.murder.user.Stat
import me.func.murder.user.User
import me.func.murder.util.GoldDropper
import java.util.*

lateinit var app: App
lateinit var goldDropper: GoldDropper
lateinit var activeBar: GameBar
var activeStatus = Status.STARTING
const val slots = 16
const val lobby = "MURL-1"

class App : JavaPlugin() {

    private val statScope = Scope("murder", Stat::class.java)
    private var userManager = BukkitUserManager(
        listOf(statScope),
        { session: Session, context -> User(session, context.getData(statScope)) },
        { user, context -> context.store(statScope, user.stat) }
    )
    lateinit var worldMeta: WorldMeta
    lateinit var timer: Timer

    override fun onEnable() {
        B.plugin = this
        app = this
        Platforms.set(PlatformDarkPaper())

        // Загрузка карты
        worldMeta = MapLoader.load("prod")!!

        // Создание раздатчика золота
        goldDropper = GoldDropper(worldMeta.getLabels("gold").map { it.toCenterLocation() })

        // Регистрация сервисов
        val core = CoreApi.get()
        core.registerService(IPartyService::class.java, PartyService(ISocketClient.get()))
        core.registerService(ITransferService::class.java, TransferService(ISocketClient.get()))
        core.registerService(IInventoryService::class.java, InventoryService())

        // Конфигурация реалма
        val info = IRealmService.get().currentRealmInfo
        info.status = RealmStatus.WAITING_FOR_PLAYERS
        info.maxPlayers = slots
        info.readableName = "Мардер #${info.realmId.id}"
        info.groupName = "Мардер #${info.realmId.id}"

        // Подключение к сервису статистики
        val kensuke = BukkitKensuke.setup(this)
        kensuke.addGlobalUserManager(userManager)
        kensuke.globalRealm = info.realmId.realmName
        userManager.isOptional = true

        // Запуск игрового таймера
        timer = Timer()
        timer.runTaskTimer(this, 10, 1)

        // Регистрация обработчиков событий
        B.events(DamageListener(), ConnectionHandler(), GlobalListeners(), GoldListener())
    }

    fun getUser(player: Player): User {
        return userManager.getUser(player)
    }

    fun getUser(uuid: UUID): User {
        return userManager.getUser(uuid)
    }
}