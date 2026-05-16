package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.CSUtility;
import com.shampaggon.crackshot.events.WeaponShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.azisaba.lgw.core.events.PlayerKillEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WeaponsSPMode implements Listener {

    private final JavaPlugin plugin;
    private final CSUtility cs = new CSUtility();
    
    // クールダウン管理
    private final Map<String, Long> cooldownMap = new HashMap<>();
    
    // キルストリーク管理（武器ごと）
    private final Map<UUID, Map<String, Integer>> weaponKillStreakMap = new HashMap<>();
    // 元の武器管理
    private final Map<UUID, String> originalWeaponMap = new HashMap<>();
    // KillStreakによる元の武器管理
    private final Map<UUID, String> killStreakOriginalWeaponMap = new HashMap<>();
    // キルストリークカウンター表示タスク
    private final Map<UUID, BukkitRunnable> counterTaskMap = new HashMap<>();
    // 時間経過武器変更タスク
    private final Map<UUID, BukkitRunnable> timedWeaponChangeTaskMap = new HashMap<>();
    
    // ジャンプ状態管理
    private final Map<UUID, Boolean> jumpMap = new HashMap<>();
    private final Map<UUID, Double> lastYMap = new HashMap<>();
    
    // オフハンド持ち替え状態管理
    private final Map<UUID, Boolean> offhandMap = new HashMap<>();

    public WeaponsSPMode(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // --- 1. シフト武器変更 ---
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        
        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        String title = cs.getWeaponTitle(item);
        
        if (title == null) return;
        
        handleWeaponChange(p, title, "Shift");
    }

    // --- 2. 弾丸切れ武器変更 ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onShoot(WeaponShootEvent event) {
        Player p = event.getPlayer();
        String title = event.getWeaponTitle();
        
        if (title == null) return;
        
        // 射撃後に弾薬チェック
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    ItemStack current = p.getInventory().getItemInMainHand();
                    String currentTitle = cs.getWeaponTitle(current);
                    
                    if (title.equals(currentTitle)) {
                        int currentAmmo = API.getCSDirector().getAmmoBetweenBrackets(p, title, current);
                        ConfigurationSection config = WeaponConfig.getWeaponConfig(title);
                        if (config != null) {
                            int magSize = config.getInt("Shoot.Capacity", 0);
                            
                            if (magSize > 0 && currentAmmo <= 0) {
                                handleWeaponChange(p, title, "Empty_Ammo");
                            }
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    // --- 3. オフハンドキー検知 ---
    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        ItemStack mainHandItem = p.getInventory().getItemInMainHand();
        String weaponTitle = cs.getWeaponTitle(mainHandItem);

        if (weaponTitle == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;

        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        if (killStreakSection == null || !killStreakSection.getBoolean("Enable", false)) return;

        ConfigurationSection eventSection = killStreakSection.getConfigurationSection("Streak_Event");
        if (eventSection == null || !eventSection.getBoolean("Enable", false)) return;

        // offhandアクションが設定されているかチェック
        boolean hasOffhandAction = false;
        for (ConfigurationSection costSec : getCostSections(eventSection)) {
            String actions = costSec.getString("Change_Weapons_WithAction", "");
            for (String action : actions.split(",")) {
                if (action.trim().equalsIgnoreCase("offhand")) {
                    hasOffhandAction = true;
                    break;
                }
            }
        }

        if (!hasOffhandAction) return;

        // オフハンドキーによる武器交換をキャンセル（武器が意図せず交換されないように）
        event.setCancelled(true);

        int currentStreak = getWeaponKillStreak(p, weaponTitle);
        checkStreakEvents(p, weaponTitle, currentStreak, "offhand");
    }
    
    // --- 4. キー combos武器変更 ---
    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        Player p = event.getPlayer();
        
        if (event.isSprinting()) {
            // ジャンプ状態をリセット
            jumpMap.put(p.getUniqueId(), false);
        }
        
        // シフトアクションをチェック
        ItemStack item = p.getInventory().getItemInMainHand();
        String title = cs.getWeaponTitle(item);
        
        if (title != null && p.isSneaking()) {
            System.out.println("[AgetarouUniqueGuns] Shift action triggered: " + title);
            handleWeaponChange(p, title, "Shift");
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        
        // 武器持ち替え時のストリークカウンター更新
        ItemStack newItem = p.getInventory().getItem(event.getNewSlot());
        String newTitle = cs.getWeaponTitle(newItem);
        
        System.out.println("[AgetarouUniqueGuns] onItemHeld: " + p.getName() + " -> " + newTitle);
        
        if (newTitle != null) {
            // 新しい武器のカウンターを開始
            startStreakCounter(p, newTitle);
            
            // 時間経過武器変更は武器を初めて手にしたときのみ開始
            if (!timedWeaponChangeTaskMap.containsKey(p.getUniqueId())) {
                startTimedWeaponChange(p, newTitle);
            }
        } else {
            // 武器でない場合はカウンターを停止
            stopStreakCounter(p);
            
            // 時間経過武器変更は停止しない（バックグラウンドで継続）
        }
        
        // オフハンド持ち替え検出
        int previousSlot = event.getPreviousSlot();
        int newSlot = event.getNewSlot();
        
        // オフハンドスロット（40）への切り替えを検出
        if (newSlot == 40 || previousSlot == 40) {
            boolean isSwitchingToOffhand = newSlot == 40;
            offhandMap.put(p.getUniqueId(), isSwitchingToOffhand);
            
            // オフハンド単体持ち替え
            if (isSwitchingToOffhand) {
                ItemStack item = p.getInventory().getItemInMainHand();
                String title = cs.getWeaponTitle(item);
                
                System.out.println("[AgetarouUniqueGuns] Offhand triggered: " + title);
                
                if (title != null) {
                    handleWeaponChange(p, title, "Offhand");
                }
            }
            
            // シフト＋オフハンド持ち替え
            if (p.isSneaking() && isSwitchingToOffhand) {
                ItemStack item = p.getInventory().getItemInMainHand();
                String title = cs.getWeaponTitle(item);
                
                System.out.println("[AgetarouUniqueGuns] Off_And_Shift triggered: " + title);
                
                if (title != null) {
                    handleWeaponChange(p, title, "Off_And_Shift");
                }
            }
            
            // ジャンプ中にオフハンド持ち替え
            if (jumpMap.getOrDefault(p.getUniqueId(), false) && isSwitchingToOffhand) {
                ItemStack item = p.getInventory().getItemInMainHand();
                String title = cs.getWeaponTitle(item);
                
                System.out.println("[AgetarouUniqueGuns] Jump_And_Off triggered: " + title);
                
                if (title != null) {
                    handleWeaponChange(p, title, "Jump_And_Off");
                }
            }
        }
    }

    // --- ジャンプ検出 ---
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        
        // Y座標の変化でジャンプを検出
        double currentY = event.getTo().getY();
        Double lastY = lastYMap.get(uuid);
        
        if (lastY != null && currentY > lastY && !p.isOnGround()) {
            // ジャンプ検出
            jumpMap.put(uuid, true);
            
            // シフト＋ジャンプ
            if (p.isSneaking()) {
                ItemStack item = p.getInventory().getItemInMainHand();
                String title = cs.getWeaponTitle(item);
                
                if (title != null) {
                    handleWeaponChange(p, title, "Jump_And_Shift");
                }
            }
            
            // ジャンプ状態を1秒後にリセット
            new BukkitRunnable() {
                @Override
                public void run() {
                    jumpMap.remove(uuid);
                }
            }.runTaskLater(plugin, 20L);
        }
        
        lastYMap.put(uuid, currentY);
    }

    // --- 4. キルストリーク武器変更 ---
    @EventHandler
    public void onPlayerKill(PlayerKillEvent event) {
        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        String title = cs.getWeaponTitle(item);
        
        if (title == null) return;
        
        // 武器固有のキルストリークを増加
        addWeaponKillStreak(p, title);
        
        // 最初のキルで元の武器を保存（WhenChangeWeapon用）
        UUID uuid = p.getUniqueId();
        if (!originalWeaponMap.containsKey(uuid)) {
            originalWeaponMap.put(uuid, title);
        }
        
        // 従来のWhenChangeWeaponキルストリークチェック
        checkWeaponChangeKillStreak(p, title);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player) {
            Player p = event.getEntity().getKiller();
            ItemStack item = p.getInventory().getItemInMainHand();
            String title = cs.getWeaponTitle(item);
            
            if (title != null) {
                // モブキルのストリークカウントチェック
                if (shouldCountMobKill(title)) {
                    addWeaponKillStreak(p, title);
                }
                
                // 従来のWhenChangeWeaponキルストリークチェック
                checkWeaponChangeKillStreak(p, title);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        UUID uuid = p.getUniqueId();
        
        // 武器固有のキルストリークを処理
        handleDeathStreakReset(p);
        
        // 元の武器に戻す（WhenChangeWeapon用）
        String originalWeapon = originalWeaponMap.remove(uuid);
        if (originalWeapon != null && !originalWeapon.isEmpty()) {
            // リスポーン後に武器を変更
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        giveWeapon(p, originalWeapon);
                        
                        // 音を再生
                        try {
                            Sound sound = Sound.valueOf("ENTITY_ITEM_BREAK");
                            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                        } catch (IllegalArgumentException ignored) {}
                        
                        // メッセージ表示
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                            new TextComponent(ChatColor.translateAlternateColorCodes('&', "&cキルストリークがリセットされました")));
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
        
        // KillStreakによる元の武器に戻す
        String killStreakOriginalWeapon = killStreakOriginalWeaponMap.remove(uuid);
        if (killStreakOriginalWeapon != null && !killStreakOriginalWeapon.isEmpty()) {
            // リスポーン後に武器を変更
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        giveWeapon(p, killStreakOriginalWeapon);
                        
                        // 音を再生
                        try {
                            Sound sound = Sound.valueOf("ENTITY_ITEM_BREAK");
                            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                        } catch (IllegalArgumentException ignored) {}
                        
                        // メッセージ表示
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                            new TextComponent(ChatColor.translateAlternateColorCodes('&', "&cKillStreak武器が元に戻りました")));
                    }
                }
            }.runTaskLater(plugin, 2L); // 少し遅らせて実行
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        weaponKillStreakMap.remove(uuid);
        jumpMap.remove(uuid);
        offhandMap.remove(uuid);
        lastYMap.remove(uuid);
        originalWeaponMap.remove(uuid);
        killStreakOriginalWeaponMap.remove(uuid);
        
        // カウンタータスクを停止
        BukkitRunnable task = counterTaskMap.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        
        // 時間経過武器変更タスクを停止
        BukkitRunnable timedTask = timedWeaponChangeTaskMap.remove(uuid);
        if (timedTask != null) {
            timedTask.cancel();
        }
    }

    // --- 武器変更処理 ---
    private void handleWeaponChange(Player p, String currentWeapon, String triggerType) {
        System.out.println("[AgetarouUniqueGuns] handleWeaponChange: " + p.getName() + " -> " + currentWeapon + " (trigger: " + triggerType + ")");
        
        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) {
            System.out.println("[AgetarouUniqueGuns] No config found for: " + currentWeapon);
            return;
        }
        
        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null || !changeSection.getBoolean("Enable", false)) return;
        
        String targetWeapon = changeSection.getString(triggerType);
        if (targetWeapon == null || targetWeapon.isEmpty()) return;
        
        // アイテム所持チェック
        if (!checkItemRequirements(p, changeSection, triggerType)) {
            return;
        }
        
        // クールダウンチェック
        String cdKey = p.getUniqueId().toString() + "_WeaponChange_" + triggerType;
        if (cooldownMap.getOrDefault(cdKey, 0L) > System.currentTimeMillis()) {
            return;
        }
        
        performWeaponChange(p, currentWeapon, targetWeapon, triggerType);
    }

    private boolean checkItemRequirements(Player p, ConfigurationSection changeSection, String triggerType) {
        ConfigurationSection ifHaveSection = changeSection.getConfigurationSection("If_HaveItems");
        if (ifHaveSection == null) return true;
        
        ConfigurationSection triggerSection = ifHaveSection.getConfigurationSection(triggerType);
        if (triggerSection == null) return true;
        
        String itemType = triggerSection.getString("Item_type");
        String itemName = triggerSection.getString("Item_name");
        int requiredAmount = triggerSection.getInt("Values", 1);
        
        if (itemType == null && itemName == null) return true;
        
        int currentAmount = 0;
        PlayerInventory inv = p.getInventory();
        
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            boolean matches = false;
            
            // アイテムタイプでチェック
            if (itemType != null) {
                try {
                    Material type = Material.valueOf(itemType.toUpperCase());
                    if (item.getType() == type) {
                        matches = true;
                    }
                } catch (IllegalArgumentException ignored) {}
            }
            
            // アイテム名でチェック
            if (!matches && itemName != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                if (displayName.equals(itemName)) {
                    matches = true;
                }
            }
            
            if (matches) {
                currentAmount += item.getAmount();
                if (currentAmount >= requiredAmount) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private void performWeaponChange(Player p, String currentWeapon, String targetWeapon, String triggerType) {
        // 武器を置き換え
        replaceWeapon(p, targetWeapon);
        
        // 音を再生
        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root != null) {
            ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
            if (changeSection != null) {
                String sound = changeSection.getString("Sound");
                if (sound != null && !sound.isEmpty()) {
                    handleFeedbackSound(p, sound);
                }
                
                // クールダウン適用
                int cooldownTicks = changeSection.getInt("CoolDown", 0);
                if (cooldownTicks > 0) {
                    String cdKey = p.getUniqueId().toString() + "_WeaponChange_" + triggerType;
                    cooldownMap.put(cdKey, System.currentTimeMillis() + (cooldownTicks * 50L));
                }
                
                // メッセージ表示
                String message = changeSection.getString("Message");
                if (message != null && !message.isEmpty()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                        new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
                }
            }
        }
    }

    // --- 武器固有キルストリークシステム ---
    
    /**
     * 武器のキルストリークを増加させる
     */
    private void addWeaponKillStreak(Player p, String weaponTitle) {
        UUID uuid = p.getUniqueId();
        Map<String, Integer> playerStreaks = weaponKillStreakMap.computeIfAbsent(uuid, k -> new HashMap<>());
        
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;
        
        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        if (killStreakSection == null || !killStreakSection.getBoolean("Enable", false)) return;
        
        int currentStreak = playerStreaks.getOrDefault(weaponTitle, 0);
        int maxCount = killStreakSection.getInt("Kill_Count", 5);
        boolean ignoreLimit = killStreakSection.getBoolean("Ignore_Limit", false);
        
        if (ignoreLimit || currentStreak < maxCount) {
            currentStreak++;
            playerStreaks.put(weaponTitle, currentStreak);
            
            // カウンター表示を開始
            startStreakCounter(p, weaponTitle);
            
            // ストリークイベントをチェック
            checkStreakEvents(p, weaponTitle, currentStreak);
        }
    }
    
    /**
     * 武器のキルストリークを取得
     */
    private int getWeaponKillStreak(Player p, String weaponTitle) {
        UUID uuid = p.getUniqueId();
        Map<String, Integer> playerStreaks = weaponKillStreakMap.get(uuid);
        return playerStreaks != null ? playerStreaks.getOrDefault(weaponTitle, 0) : 0;
    }
    
    /**
     * 武器のキルストリークを消費
     */
    private boolean consumeWeaponKillStreak(Player p, String weaponTitle, int amount) {
        UUID uuid = p.getUniqueId();
        Map<String, Integer> playerStreaks = weaponKillStreakMap.get(uuid);
        if (playerStreaks == null) return false;
        
        int currentStreak = playerStreaks.getOrDefault(weaponTitle, 0);
        if (currentStreak >= amount) {
            playerStreaks.put(weaponTitle, currentStreak - amount);
            return true;
        }
        return false;
    }
    
    /**
     * 死亡時のストリークリセット処理
     */
    private void handleDeathStreakReset(Player p) {
        UUID uuid = p.getUniqueId();
        Map<String, Integer> playerStreaks = weaponKillStreakMap.get(uuid);
        if (playerStreaks == null) return;
        
        // 手持ちの武器を取得
        ItemStack item = p.getInventory().getItemInMainHand();
        String currentWeapon = cs.getWeaponTitle(item);
        
        if (currentWeapon != null && playerStreaks.containsKey(currentWeapon)) {
            ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
            if (root != null) {
                ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
                if (killStreakSection != null && killStreakSection.getBoolean("Enable", false)) {
                    boolean removeStreak = killStreakSection.getBoolean("Remove_Streak", true);
                    
                    if (removeStreak) {
                        int removeAmount = killStreakSection.getInt("Remove_Several_Streak", -1);
                        if (removeAmount == -1) {
                            // 全て削除
                            playerStreaks.remove(currentWeapon);
                        } else {
                            // 指定数削除
                            int current = playerStreaks.get(currentWeapon);
                            playerStreaks.put(currentWeapon, Math.max(0, current - removeAmount));
                        }
                        
                        // カウンタータスクを停止
                        stopStreakCounter(p);
                    }
                }
            }
        }
    }
    
    /**
     * モブキルをカウントするかチェック
     */
    private boolean shouldCountMobKill(String weaponTitle) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return false;
        
        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        return killStreakSection != null && 
               killStreakSection.getBoolean("Enable", false) && 
               killStreakSection.getBoolean("Accumulate_Streaks_Defeat_Mobs", false);
    }
    
    /**
     * 従来のWhenChangeWeaponキルストリークチェック
     */
    private void checkWeaponChangeKillStreak(Player p, String title) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(title);
        if (root == null) return;
        
        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null || !changeSection.getBoolean("Enable", false)) return;
        
        ConfigurationSection killStreakSection = changeSection.getConfigurationSection("Kill_Streak");
        if (killStreakSection == null) return;
        
        int currentStreak = getWeaponKillStreak(p, title);
        
        for (String streakStr : killStreakSection.getKeys(false)) {
            try {
                int requiredStreak = Integer.parseInt(streakStr);
                if (currentStreak == requiredStreak) {
                    String targetWeapon = killStreakSection.getString(streakStr);
                    if (targetWeapon != null && !targetWeapon.isEmpty()) {
                        performWeaponChange(p, title, targetWeapon, "Kill_Streak_" + streakStr);
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // --- キルストリークカウンター表示 ---
    
    /**
     * キルストリークカウンターを開始
     */
    private void startStreakCounter(Player p, String weaponTitle) {
        stopStreakCounter(p); // 既存のタスクを停止
        
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;
        
        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        if (killStreakSection == null || !killStreakSection.getBoolean("Enable", false)) return;
        
        ConfigurationSection iconSection = killStreakSection.getConfigurationSection("Streak_Icon");
        if (iconSection == null || !iconSection.getBoolean("Enable", false)) return;
        
        String leftSymbol = iconSection.getString("Left", "▶");
        String rightSymbol = iconSection.getString("Right", "◀");
        int maxCount = killStreakSection.getInt("Kill_Count", 5);
        
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    this.cancel();
                    return;
                }
                
                // 手持ちの武器が同じかチェック
                ItemStack currentItem = p.getInventory().getItemInMainHand();
                String currentTitle = cs.getWeaponTitle(currentItem);
                
                if (weaponTitle.equals(currentTitle)) {
                    int currentStreak = getWeaponKillStreak(p, weaponTitle);
                    String counter = buildStreakCounter(currentStreak, maxCount, leftSymbol, rightSymbol);
                    
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                        new TextComponent(ChatColor.translateAlternateColorCodes('&', counter)));
                } else {
                    // 武器が変わったら停止
                    this.cancel();
                    counterTaskMap.remove(p.getUniqueId());
                }
            }
        };
        
        task.runTaskTimer(plugin, 0L, 5L); // 5tickごとに更新
        counterTaskMap.put(p.getUniqueId(), task);
    }
    
    /**
     * キルストリークカウンターを停止
     */
    private void stopStreakCounter(Player p) {
        BukkitRunnable task = counterTaskMap.remove(p.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * キルストリークカウンター文字列を生成
     */
    private String buildStreakCounter(int current, int max, String leftSymbol, String rightSymbol) {
        StringBuilder sb = new StringBuilder();
        
        // 左側（貯まった分）
        sb.append("&a");
        for (int i = 0; i < current; i++) {
            sb.append(leftSymbol);
        }
        
        // 右側（残り分）
        sb.append("&7");
        for (int i = current; i < max; i++) {
            sb.append(rightSymbol);
        }
        
        return sb.toString();
    }
    
    // --- ストリークイベントシステム ---
    
    /**
     * ストリークイベントをチェック
     */
    // checkStreakEvents にトリガー種別を追加
    private void checkStreakEvents(Player p, String weaponTitle, int currentStreak) {
        checkStreakEvents(p, weaponTitle, currentStreak, null);
    }

    private void checkStreakEvents(Player p, String weaponTitle, int currentStreak, String triggerAction) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;

        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        if (killStreakSection == null) return;

        ConfigurationSection eventSection = killStreakSection.getConfigurationSection("Streak_Event");
        if (eventSection == null || !eventSection.getBoolean("Enable", false)) return;

        int maxCount = killStreakSection.getInt("Kill_Count", 5);

        // 満タン時 → Cost_MaxCount を優先
        if (currentStreak >= maxCount) {
            ConfigurationSection maxEventSection = eventSection.getConfigurationSection("Cost_MaxCount");
            if (maxEventSection != null) {
                executeStreakEvent(p, weaponTitle, maxEventSection, true, triggerAction);
                return; // Cost_MaxCountが発火したらCost_Countは見ない
            }
        }

        // 通常消費イベント
        ConfigurationSection costSection = eventSection.getConfigurationSection("Cost_Count");
        if (costSection != null) {
            int costAmount = costSection.getInt("Cost_Count", 1);
            if (currentStreak >= costAmount) {
                executeStreakEvent(p, weaponTitle, costSection, false, triggerAction);
            } else {
                // ストリーク不足メッセージ（offhandトリガー時のみ表示）
                if (triggerAction != null) {
                    String notEnoughActionbar = costSection.getString("Notenough_Actionbar");
                    if (notEnoughActionbar != null && !notEnoughActionbar.isEmpty()) {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                new TextComponent(ChatColor.translateAlternateColorCodes('&', notEnoughActionbar)));
                    }
                    String notEnoughChat = costSection.getString("Notenough_Saychat");
                    if (notEnoughChat != null && !notEnoughChat.isEmpty()) {
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&', notEnoughChat));
                    }
                }
            }
        }
    }
    
    /**
     * ストリークイベントを実行
     */
    // executeStreakEvent にもtriggerActionを追加
    private void executeStreakEvent(Player p, String weaponTitle, ConfigurationSection eventConfig, boolean isMaxEvent) {
        executeStreakEvent(p, weaponTitle, eventConfig, isMaxEvent, null);
    }

    private void executeStreakEvent(Player p, String weaponTitle, ConfigurationSection eventConfig, boolean isMaxEvent, String triggerAction) {
        String requiredActions = eventConfig.getString("Change_Weapons_WithAction", "");

        // アクション条件チェック
        if (!requiredActions.isEmpty()) {
            boolean actionMatched = false;
            for (String action : requiredActions.split(",")) {
                action = action.trim().toLowerCase();
                switch (action) {
                    case "offhand":
                        // triggerActionが"offhand"のときだけ発火（PlayerSwapHandItemsEvent経由）
                        if ("offhand".equals(triggerAction)) actionMatched = true;
                        break;
                    case "shift":
                        if (p.isSneaking()) actionMatched = true;
                        break;
                    case "jump":
                        if (jumpMap.getOrDefault(p.getUniqueId(), false)) actionMatched = true;
                        break;
                    default:
                        actionMatched = true;
                        break;
                }
                if (actionMatched) break;
            }
            if (!actionMatched) return;
        }

        // ストリークを消費
        int consumeAmount = isMaxEvent ? getWeaponKillStreak(p, weaponTitle) : eventConfig.getInt("Cost_Count", 1);
        if (!consumeWeaponKillStreak(p, weaponTitle, consumeAmount)) return;

        // 武器変更
        String changeWeapon = eventConfig.getString("Change_Weapons");
        if (changeWeapon != null && !changeWeapon.isEmpty()) {
            UUID uuid = p.getUniqueId();
            if (!killStreakOriginalWeaponMap.containsKey(uuid)) {
                killStreakOriginalWeaponMap.put(uuid, weaponTitle);
            }

            boolean takeover = eventConfig.getBoolean("Takeover_Streak", false);
            if (takeover) {
                int remainingStreak = getWeaponKillStreak(p, weaponTitle);
                Map<String, Integer> playerStreaks = weaponKillStreakMap.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
                playerStreaks.put(changeWeapon, remainingStreak);
            }

            replaceWeapon(p, changeWeapon);
        }

        // コマンド実行
        String cmd = eventConfig.getString("Cmd");
        if (cmd != null && !cmd.isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("#shooter#", p.getName()));
        }

        // アクションバーメッセージ
        String actionbarMsg = eventConfig.getString("Actionbar");
        if (actionbarMsg != null && !actionbarMsg.isEmpty()) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.translateAlternateColorCodes('&', actionbarMsg)));
        }

        // チャットメッセージ
        String chatMsg = eventConfig.getString("Saychat");
        if (chatMsg != null && !chatMsg.isEmpty()) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', chatMsg));
        }

        // 音再生
        String sound = eventConfig.getString("Sound");
        if (sound != null && !sound.isEmpty()) {
            handleFeedbackSound(p, sound);
        }
    }

    // Cost_MaxCount と Cost_Count の両セクションをまとめて返すユーティリティ
    private java.util.List<ConfigurationSection> getCostSections(ConfigurationSection eventSection) {
        java.util.List<ConfigurationSection> list = new java.util.ArrayList<>();
        ConfigurationSection maxSec = eventSection.getConfigurationSection("Cost_MaxCount");
        if (maxSec != null) list.add(maxSec);
        ConfigurationSection costSec = eventSection.getConfigurationSection("Cost_Count");
        if (costSec != null) list.add(costSec);
        return list;
    }
    
    /**
     * 音を再生（UniversalWeaponSystemと同様の形式）
     */
    private void handleFeedbackSound(Player p, String rawSounds) {
        for (String entry : rawSounds.split(",")) {
            String[] parts = entry.trim().split("-");
            if (parts.length == 0 || parts[0].isEmpty()) continue;
            try {
                Sound sound = Sound.valueOf(parts[0].toUpperCase());
                float volume = (parts.length > 1) ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = (parts.length > 2) ? Float.parseFloat(parts[2]) : 1.0f;
                int delay = (parts.length > 3) ? Integer.parseInt(parts[3]) : 0;
                if (delay <= 0) p.playSound(p.getLocation(), sound, volume, pitch);
                else {
                    new BukkitRunnable() {
                        @Override public void run() { if (p.isOnline()) p.playSound(p.getLocation(), sound, volume, pitch); }
                    }.runTaskLater(plugin, (long) delay);
                }
            } catch (Exception ignored) {}
        }
    }

    // --- 時間経過武器変更システム ---
    
    /**
     * 時間経過武器変更を開始
     */
    private void startTimedWeaponChange(Player p, String weaponTitle) {
        System.out.println("[AgetarouUniqueGuns] startTimedWeaponChange called for: " + weaponTitle);
        stopTimedWeaponChange(p); // 既存のタスクを停止
        
        // 設定を直接読み込み（キャッシュを回避）
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) {
            System.out.println("[AgetarouUniqueGuns] No config found for Timed_Change: " + weaponTitle);
            return;
        }
        
        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null || !changeSection.getBoolean("Enable", false)) {
            System.out.println("[AgetarouUniqueGuns] WhenChangeWeapon not enabled for: " + weaponTitle);
            return;
        }
        
        ConfigurationSection timedSection = changeSection.getConfigurationSection("Timed_Change");
        if (timedSection == null) {
            System.out.println("[AgetarouUniqueGuns] Timed_Change section not found for: " + weaponTitle);
            return;
        }
        
        int delayTicks = timedSection.getInt("Delay_Ticks", 0);
        String targetWeapon = timedSection.getString("Target_Weapon");
        boolean returnOnEmpty = timedSection.getBoolean("Return_On_Empty_Ammo", false);
        
        System.out.println("[AgetarouUniqueGuns] Timed_Change config: delay=" + delayTicks + ", target=" + targetWeapon);
        
        if (delayTicks <= 0 || (targetWeapon == null || targetWeapon.isEmpty())) {
            System.out.println("[AgetarouUniqueGuns] Invalid Timed_Change config for: " + weaponTitle);
            return;
        }
        
        // 元の武器を保存
        UUID uuid = p.getUniqueId();
        if (!originalWeaponMap.containsKey(uuid)) {
            originalWeaponMap.put(uuid, weaponTitle);
        }
        
        // 武器切り替えイベントが発生したのでDelay_Barを開始
        if (timedSection.contains("Delay_Bar")) {
            ConfigurationSection delayBarSection = timedSection.getConfigurationSection("Delay_Bar");
            if (delayBarSection != null && delayBarSection.getBoolean("Enable", false)) {
                startTimedDelayBar(p, delayBarSection, delayTicks, weaponTitle, targetWeapon);
            }
        }
        
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    this.cancel();
                    return;
                }
                
                // 武器を置き換え（手持ちチェックなし）
                replaceWeapon(p, targetWeapon);
                
                // 音を再生
                String sound = timedSection.getString("Sound");
                if (sound != null && !sound.isEmpty()) {
                    handleFeedbackSound(p, sound);
                }
                
                // メッセージ表示
                String message = timedSection.getString("Message");
                if (message != null && !message.isEmpty()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                        new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
                }
                
                // 弾切れ時は既存のEmpty_Ammo機能で対応
                // Return_On_Empty_Ammo設定は既存機能に任せる
                
                this.cancel();
                timedWeaponChangeTaskMap.remove(uuid);
            }
        };
        
        task.runTaskLater(plugin, delayTicks);
        timedWeaponChangeTaskMap.put(uuid, task);
    }
    
    /**
     * 時間経過武器変更を停止
     */
    private void stopTimedWeaponChange(Player p) {
        BukkitRunnable task = timedWeaponChangeTaskMap.remove(p.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
    
    
    // --- Delay Bar機能 ---
    
    private void startTimedDelayBar(Player p, ConfigurationSection sec, int ticks, String weaponTitle, String targetWeapon) {
        new BukkitRunnable() {
            int i = 0;
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }
                
                // 手持ちの武器が同じかチェック
                ItemStack currentItem = p.getInventory().getItemInMainHand();
                String currentTitle = cs.getWeaponTitle(currentItem);
                
                if (currentTitle != null && currentTitle.equals(weaponTitle)) {
                    if (i >= ticks) {
                        String endMsg = sec.getString("End_Action_Bar");
                        if (endMsg != null && !endMsg.isEmpty()) {
                            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', endMsg)));
                        }
                        String endSound = sec.getString("End_Sound");
                        if (endSound != null && !endSound.isEmpty()) {
                            handleFeedbackSound(p, endSound);
                        }
                        this.cancel();
                        return;
                    }
                    
                    String actionStr = sec.getString("Action_Bar");
                    if (actionStr != null) {
                        String bar = buildBar((double) i / ticks, sec);
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', actionStr.replace("{bar}", bar))));
                    }
                    i += 2;
                } else {
                    // 武器が変わったら停止
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
    
    private void startDelayBar(Player p, ConfigurationSection sec, int ticks, String weaponTitle) {
        new BukkitRunnable() {
            int i = 0;
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }
                if (i >= ticks) {
                    String endMsg = sec.getString("End_Action_Bar");
                    if (endMsg != null && !endMsg.isEmpty()) {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', endMsg)));
                    }
                    String endSound = sec.getString("End_Sound");
                    if (endSound != null && !endSound.isEmpty()) {
                        handleFeedbackSound(p, endSound);
                    }
                    this.cancel();
                    return;
                }
                ItemStack currentItem = p.getInventory().getItemInMainHand();
                String currentTitle = cs.getWeaponTitle(currentItem);
                if (currentTitle != null && currentTitle.equals(weaponTitle)) {
                    String actionStr = sec.getString("Action_Bar");
                    if (actionStr != null) {
                        String bar = buildBar((double) i / ticks, sec);
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.translateAlternateColorCodes('&', actionStr.replace("{bar}", bar))));
                    }
                }
                i += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
    
    private String buildBar(double pct, ConfigurationSection sec) {
        int len = sec.getInt("Symbol_Amount", 15);
        int left = (int) (pct * len);
        String sym = sec.getString("Symbol", "|");
        return ChatColor.translateAlternateColorCodes('&', sec.getString("Left_Color", "&a")) + repeat(sym, left) + ChatColor.translateAlternateColorCodes('&', sec.getString("Right_Color", "&c")) + repeat(sym, len - left);
    }
    
    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private void replaceWeapon(Player p, String weaponName) {
        // 手持ちの武器を新しい武器に置き換え
        ItemStack currentWeapon = p.getInventory().getItemInMainHand();
        String currentTitle = cs.getWeaponTitle(currentWeapon);
        
        if (currentTitle != null) {
            // 現在の武器を削除
            p.getInventory().setItemInMainHand(null);
            
            // 新しい武器を付与
            cs.giveWeapon(p, weaponName, 1);
            
            // メインハンドに切り替え
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        PlayerInventory inv = p.getInventory();
                        for (int i = 0; i < 9; i++) {
                            ItemStack item = inv.getItem(i);
                            if (item != null && weaponName.equals(cs.getWeaponTitle(item))) {
                                p.getInventory().setHeldItemSlot(i);
                                break;
                            }
                        }
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }
    
    private void giveWeapon(Player p, String weaponName) {
        // CrackShotの武器をプレイヤーに渡す
        cs.giveWeapon(p, weaponName, 1);
        
        // メインハンドに切り替え
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    PlayerInventory inv = p.getInventory();
                    for (int i = 0; i < 9; i++) {
                        ItemStack item = inv.getItem(i);
                        if (item != null && weaponName.equals(cs.getWeaponTitle(item))) {
                            p.getInventory().setHeldItemSlot(i);
                            break;
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }
}
