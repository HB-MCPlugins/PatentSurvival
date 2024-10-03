package com.hwibaek.patentSurvival

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class PatentSurvival : JavaPlugin(), Listener {

    private val patents = mutableMapOf<Material, UUID>()
    private val patentsOwner = mutableMapOf<UUID, MutableList<Material>>()
    private val openSource = mutableListOf<Material>()
    
    private fun convertItem(input: String): Material? {
        try {
            val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM)
            val mat = registry.get(TypedKey.create(RegistryKey.ITEM, Key.key(input)))

            return mat?.createItemStack()?.type
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun onEnable() {
        saveDefaultConfig()
        
        val list = config.getStringList("open_source_items")
        list.forEach {
            value ->
            val converted = convertItem(value)
            if (converted != null) {
                openSource.add(converted)
            }
            else {
                logger.warning("$value is not a valid item. check this!")
            }
        }
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        
    }
    
    @EventHandler
    fun onCraft(e: CraftItemEvent) {
        val crafter = e.whoClicked
        val crafterId = crafter.uniqueId
        val craftedItem = e.currentItem
        if (craftedItem == null) {
            return
        }
        
        val craftedMaterial = craftedItem.type
        if (openSource.contains(craftedMaterial)) {
            return
        }
        if (!patents.containsKey(craftedMaterial)) {
            //특허 리스트에 아이템 등록
            patents.put(e.currentItem!!.type, crafterId)
            //특허권자 신규등록
            if (!patentsOwner.containsKey(crafterId)) {
                patentsOwner.put(crafterId, mutableListOf())
            }
            //특허권자 리스트에 아이템 등록
            patentsOwner[crafterId]?.add(craftedMaterial)
            //특허 등록 전체메시지
            if (config.getBoolean("broadcast_new_patent")) {
                Bukkit.broadcast(Component.text("${crafter.name}님이 ${craftedMaterial.name}에 대한 새로운 특허를 등록했습니다!"))
            }
            else {
                val trueCrafter = crafter as Player
                trueCrafter.sendMessage(Component.text("${craftedMaterial.name}에 대한 특허가 등록되었습니다!"))
            }
            //특허를 너무 많이 갖고있으면 가장 오래된 특허 취소
            if (patentsOwner[crafterId]!!.size > config.getInt("max_patent_count") && config.getInt("max_patent_count") != 0) {
                val old = patentsOwner[crafterId]!![0]
                patents.remove(old)
                patentsOwner[crafterId]!!.remove(old)
                //특허 만료 전체메시지
                if (config.getBoolean("broadcast_patent_expiration")) {
                    Bukkit.broadcast(Component.text("${crafter.name}님의 ${old.name}에 대한 특허가 만료되었습니다!"))
                }
                else {
                    val trueCrafter = crafter as Player
                    trueCrafter.sendMessage(Component.text("당신의 ${old.name}에 대한 특허가 만료되었습니다!"))
                }
            }
        }
        else if (crafterId != patents[craftedMaterial]) {
            when (config.getInt("patent_treatment")) {
                1 -> e.isCancelled = true
                2 -> {
                    val owner = Bukkit.getOfflinePlayer(patents[craftedMaterial]!!)
                    if (owner.player != null) {
                        owner.player?.inventory!!.addItem(craftedItem)
                    }
                    e.isCancelled = true
                    e.inventory.clear()
                }
                3 -> {
                    e.isCancelled = true
                    e.inventory.clear()
                }
            }
            when (config.getInt("tea_bagging_message")) {
                1 -> {
                    val owner = Bukkit.getOfflinePlayer(patents[craftedMaterial]!!)
                    val trueCrafter = crafter as Player
                    
                    owner.player?.sendMessage(Component.text("${trueCrafter.name}님이 ${craftedMaterial.name}을 만드려다 실패했습니다!",
                        NamedTextColor.RED))
                    trueCrafter.sendMessage(Component.text("당신은 ${owner.name}님이 특허를 낸 ${craftedMaterial.name}을 만드려다 실패했습니다!",
                        NamedTextColor.RED))
                }
                2 -> {
                    val owner = Bukkit.getOfflinePlayer(patents[craftedMaterial]!!)
                    val trueCrafter = crafter as Player

                    Bukkit.broadcast(Component.text("${trueCrafter.name}님이 ${owner.name}님이 특허를 낸 ${craftedMaterial.name}을 만드려다 실패했습니다!",
                        NamedTextColor.RED))
                }
            }
        }
    }
}
