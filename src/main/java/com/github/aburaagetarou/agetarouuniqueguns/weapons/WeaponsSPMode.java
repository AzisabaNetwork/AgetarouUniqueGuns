package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
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
    private final Map<UUID, Map<String, Integer>> sharedStreakMap = new HashMap<>();
    private final Map<UUID, Integer> actionBarPauseMap = new HashMap<>();

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

        // WhenChangeWeapon の Shift
        handleWeaponChange(p, title, "Shift");

        // KillStreak の Streak_Event の shift
        int currentStreak = getWeaponKillStreak(p, title);
        checkStreakEvents(p, title, currentStreak, "shift");
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

        // WhenChangeWeapon の Off_And_Shift（シフト押しながらFキー）
        if (p.isSneaking()) {
            ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
            if (root != null) {
                ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
                if (changeSection != null && changeSection.getBoolean("Enable", false)) {
                    String target = changeSection.getString("Off_And_Shift");
                    if (target != null && !target.isEmpty()) {
                        event.setCancelled(true);
                        handleWeaponChange(p, weaponTitle, "Off_And_Shift");
                        return;
                    }
                }
            }
        }

        // KillStreak の offhand処理（既存）
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;

        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        if (killStreakSection == null || !killStreakSection.getBoolean("Enable", false)) return;

        ConfigurationSection eventSection = killStreakSection.getConfigurationSection("Streak_Event");
        if (eventSection == null || !eventSection.getBoolean("Enable", false)) return;

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
        actionBarPauseMap.remove(uuid); // ← 追加
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
                // Title / Subtitle
                String title = changeSection.getString("Title");
                String subtitle = changeSection.getString("Subtitle");
                if (title != null || subtitle != null) {
                    String t = title != null ? ChatColor.translateAlternateColorCodes('&', title) : "";
                    String st = subtitle != null ? ChatColor.translateAlternateColorCodes('&', subtitle) : "";
                    p.sendTitle(t, st, 10, 70, 20);
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

        String key = getStreakKey(weaponTitle);
        int currentStreak = playerStreaks.getOrDefault(key, 0);
        int maxCount = killStreakSection.getInt("Kill_Count", 5);
        boolean ignoreLimit = killStreakSection.getBoolean("Ignore_Limit", false);

        if (ignoreLimit || currentStreak < maxCount) {
            currentStreak++;
            playerStreaks.put(key, currentStreak);
            startStreakCounter(p, weaponTitle);
            checkStreakEvents(p, weaponTitle, currentStreak);
        }
    }

    /**
     * 武器のストリークキーを取得（Streak_Share_Key があればそれ、なければ武器名）
     */
    private String getStreakKey(String weaponTitle) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return weaponTitle;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null) return weaponTitle;
        String shareKey = ks.getString("Streak_Share_Key");
        return (shareKey != null && !shareKey.isEmpty()) ? shareKey : weaponTitle;
    }

    /**
     * 武器のキルストリークを取得
     */
    private int getWeaponKillStreak(Player p, String weaponTitle) {
        String key = getStreakKey(weaponTitle);
        Map<String, Integer> streaks = weaponKillStreakMap.get(p.getUniqueId());
        return streaks != null ? streaks.getOrDefault(key, 0) : 0;
    }

    /**
     * 武器のキルストリークを消費
     */
    private boolean consumeWeaponKillStreak(Player p, String weaponTitle, int amount) {
        String key = getStreakKey(weaponTitle);
        Map<String, Integer> playerStreaks = weaponKillStreakMap.get(p.getUniqueId());
        if (playerStreaks == null) return false;

        int current = playerStreaks.getOrDefault(key, 0);
        if (current >= amount) {
            playerStreaks.put(key, current - amount);
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

        ItemStack item = p.getInventory().getItemInMainHand();
        String currentWeapon = cs.getWeaponTitle(item);
        if (currentWeapon == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) return;
        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        if (killStreakSection == null || !killStreakSection.getBoolean("Enable", false)) return;

        boolean removeStreak = killStreakSection.getBoolean("Remove_Streak", true);
        if (!removeStreak) return;

        String key = getStreakKey(currentWeapon);
        int removeAmount = killStreakSection.getInt("Remove_Several_Streak", -1);
        if (removeAmount == -1) {
            playerStreaks.remove(key);
        } else {
            int current = playerStreaks.getOrDefault(key, 0);
            playerStreaks.put(key, Math.max(0, current - removeAmount));
        }
        stopStreakCounter(p);
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
        stopStreakCounter(p);

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
                if (!p.isOnline()) { this.cancel(); return; }

                // 一時停止中はアクションバーを上書きしない
                UUID uuid = p.getUniqueId();
                int pauseRemaining = actionBarPauseMap.getOrDefault(uuid, 0);
                if (pauseRemaining > 0) {
                    actionBarPauseMap.put(uuid, pauseRemaining - 5);
                    return;
                }

                int currentStreak = getWeaponKillStreak(p, weaponTitle);
                String counter = buildStreakCounter(currentStreak, maxCount, leftSymbol, rightSymbol);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.translateAlternateColorCodes('&', counter)));
            }
        };

        task.runTaskTimer(plugin, 0L, 5L);
        counterTaskMap.put(p.getUniqueId(), task);
    }

    /**
     * アクションバーにNotenoughメッセージを送り、指定tick間カウンター表示を一時停止する
     */
    private void sendNotenoughActionbar(Player p, String message, int pauseTicks) {
        actionBarPauseMap.put(p.getUniqueId(), pauseTicks);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
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
        boolean isMax = currentStreak >= maxCount;

        ConfigurationSection maxSec = eventSection.getConfigurationSection("Cost_MaxCount");
        ConfigurationSection costSec = eventSection.getConfigurationSection("Cost_Count");

        // Cost_MaxCount と Cost_Count のアクションを取得
        String maxAction = maxSec != null ? maxSec.getString("Change_Weapons_WithAction", "") : "";
        String costAction = costSec != null ? costSec.getString("Change_Weapons_WithAction", "") : "";

        // 満タン時の処理
        if (isMax && maxSec != null) {
            boolean maxActionMatch = matchesAction(maxAction, triggerAction, p);
            if (maxActionMatch) {
                // Cost_MaxCount を発火
                // Cost_Count と同じアクションの場合はMaxを優先してCost_Countは発火しない
                executeStreakEvent(p, weaponTitle, maxSec, true, triggerAction);
                if (maxAction.equalsIgnoreCase(costAction)) return;
            }
        }

        // Cost_Count の処理（満タン時でもアクションが違えば発火）
        if (costSec != null) {
            if (!costAction.isEmpty() && triggerAction == null) return;
            int costAmount = costSec.getInt("Cost_Count", 1);

            if (currentStreak >= costAmount) {
                boolean costActionMatch = matchesAction(costAction, triggerAction, p);
                if (costActionMatch) {
                    executeStreakEvent(p, weaponTitle, costSec, false, triggerAction);
                }
            } else if (triggerAction != null) {
                String notEnoughActionbar = costSec.getString("Notenough_Actionbar");
                if (notEnoughActionbar != null && !notEnoughActionbar.isEmpty()) {
                    sendNotenoughActionbar(p, notEnoughActionbar, 40);
                }
                String notEnoughChat = costSec.getString("Notenough_Saychat");
                if (notEnoughChat != null && !notEnoughChat.isEmpty()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', notEnoughChat));
                }
            }
        }

        // 満タン未満でoffhandのとき Cost_MaxCount の Notenough を表示
        if (!isMax && "offhand".equals(triggerAction) && maxSec != null) {
            String notEnoughActionbar = maxSec.getString("Notenough_Actionbar");
            if (notEnoughActionbar != null && !notEnoughActionbar.isEmpty()) {
                sendNotenoughActionbar(p, notEnoughActionbar, 40);
            }
        }
    }

    /**
     * アクション条件が一致するか判定
     */
    private boolean matchesAction(String requiredActions, String triggerAction, Player p) {
        if (requiredActions.isEmpty()) {
            // WithAction未設定 = キル時自動発火のみ（triggerAction==nullのとき）
            return triggerAction == null;
        }
        for (String action : requiredActions.split(",")) {
            action = action.trim().toLowerCase();
            switch (action) {
                case "offhand": if ("offhand".equals(triggerAction)) return true; break;
                case "shift":   if ("shift".equals(triggerAction) || p.isSneaking()) return true; break;
                case "jump":    if ("jump".equals(triggerAction) || jumpMap.getOrDefault(p.getUniqueId(), false)) return true; break;
            }
        }
        return false;
    }

    /**
     * ストリークイベントを実行
     */
    // executeStreakEvent にもtriggerActionを追加
    private void executeStreakEvent(Player p, String weaponTitle, ConfigurationSection eventConfig, boolean isMaxEvent) {
        executeStreakEvent(p, weaponTitle, eventConfig, isMaxEvent, null);
    }

    private void executeStreakEvent(Player p, String weaponTitle, ConfigurationSection eventConfig, boolean isMaxEvent, String triggerAction) {
        // アクション判定は checkStreakEvents 側で済んでいるのでここでは行わない

        // streak引き継ぎ処理（MCX→MCX2などMax消費時も引き継ぐ）
        String changeWeapon = eventConfig.getString("Change_Weapons");
        int consumeAmount;
        if (isMaxEvent) {
            // 全消費ではなくKill_Count分だけ消費し、残りを引き継ぎ先に移す
            ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
            int maxCount = root != null && root.contains("KillStreak")
                    ? root.getConfigurationSection("KillStreak").getInt("Kill_Count", 5) : 5;
            consumeAmount = maxCount;
        } else {
            consumeAmount = eventConfig.getInt("Cost_Count", 1);
        }
        if (!consumeWeaponKillStreak(p, weaponTitle, consumeAmount)) return;

        if (changeWeapon != null && !changeWeapon.isEmpty()) {
            UUID uuid = p.getUniqueId();
            if (!killStreakOriginalWeaponMap.containsKey(uuid)) {
                killStreakOriginalWeaponMap.put(uuid, weaponTitle);
            }

            boolean takeover = eventConfig.getBoolean("Takeover_Streak", false);
            if (takeover) {
                int remainingStreak = getWeaponKillStreak(p, weaponTitle);
                weaponKillStreakMap.computeIfAbsent(uuid, k -> new HashMap<>())
                        .put(getStreakKey(changeWeapon), remainingStreak);
            }

            if ("offhand".equals(triggerAction)) {
                final String fw = changeWeapon;
                new BukkitRunnable() {
                    @Override public void run() { if (p.isOnline()) replaceWeapon(p, fw); }
                }.runTaskLater(plugin, 1L);
            } else {
                replaceWeapon(p, changeWeapon);
            }
        }

        // コマンド実行
        String cmd = eventConfig.getString("Cmd");
        if (cmd != null && !cmd.isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("#shooter#", p.getName()));
        }

        // アクションバー
        String actionbarMsg = eventConfig.getString("Actionbar");
        if (actionbarMsg != null && !actionbarMsg.isEmpty()) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.translateAlternateColorCodes('&', actionbarMsg)));
        }

        // チャット
        String chatMsg = eventConfig.getString("Saychat");
        if (chatMsg != null && !chatMsg.isEmpty()) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', chatMsg));
        }

        // Title / Subtitle
        String title = eventConfig.getString("Title");
        String subtitle = eventConfig.getString("Subtitle");
        if (title != null || subtitle != null) {
            String t = title != null ? ChatColor.translateAlternateColorCodes('&', title) : "";
            String st = subtitle != null ? ChatColor.translateAlternateColorCodes('&', subtitle) : "";
            p.sendTitle(t, st, 10, 70, 20);
        }

        // 音
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
        stopTimedWeaponChange(p);

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;

        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null || !changeSection.getBoolean("Enable", false)) return;

        ConfigurationSection timedSection = changeSection.getConfigurationSection("Timed_Change");
        if (timedSection == null) return;

        int delayTicks = timedSection.getInt("Delay_Ticks", 0);
        String targetWeapon = timedSection.getString("Target_Weapon");

        if (delayTicks <= 0 || targetWeapon == null || targetWeapon.isEmpty()) return;

        UUID uuid = p.getUniqueId();

        // Delay_Bar を即時開始（持ち替え不要）
        ConfigurationSection delayBarSection = timedSection.getConfigurationSection("Delay_Bar");
        if (delayBarSection != null && delayBarSection.getBoolean("Enable", false)) {
            startTimedDelayBar(p, delayBarSection, delayTicks, weaponTitle, targetWeapon);
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }

                replaceWeapon(p, targetWeapon);

                String sound = timedSection.getString("Sound");
                if (sound != null && !sound.isEmpty()) handleFeedbackSound(p, sound);

                String message = timedSection.getString("Message");
                if (message != null && !message.isEmpty()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
                }

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

                if (i >= ticks) {
                    String endMsg = sec.getString("End_Action_Bar");
                    if (endMsg != null && !endMsg.isEmpty()) {
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                new TextComponent(ChatColor.translateAlternateColorCodes('&', endMsg)));
                    }
                    String endSound = sec.getString("End_Sound");
                    if (endSound != null && !endSound.isEmpty()) handleFeedbackSound(p, endSound);
                    this.cancel();
                    return;
                }

                // 持ち替えチェック廃止 → 常時表示
                String actionStr = sec.getString("Action_Bar");
                if (actionStr != null) {
                    String bar = buildBar((double) i / ticks, sec);
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.translateAlternateColorCodes('&', actionStr.replace("{bar}", bar))));
                }
                i += 2;
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
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;

                PlayerInventory inv = p.getInventory();
                int replaceSlot = inv.getHeldItemSlot(); // 現在のスロットを記憶

                // メインハンドの武器を消す（空でも強制上書き）
                inv.setItem(replaceSlot, null);

                // 新しい武器をインベントリに追加（空き枠へ）
                cs.giveWeapon(p, weaponName, 1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) return;
                        // 付与された新武器を元の枠へ移動
                        for (int i = 0; i < inv.getSize(); i++) {
                            ItemStack item = inv.getItem(i);
                            if (item != null && weaponName.equals(cs.getWeaponTitle(item))) {
                                if (i != replaceSlot) {
                                    inv.setItem(replaceSlot, item);
                                    inv.setItem(i, null);
                                }
                                inv.setHeldItemSlot(replaceSlot);
                                break;
                            }
                        }
                        startStreakCounter(p, weaponName);
                        startTimedWeaponChange(p, weaponName);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void giveWeapon(Player p, String weaponName) {
        PlayerInventory inv = p.getInventory();
        int targetSlot = inv.getHeldItemSlot();

        cs.giveWeapon(p, weaponName, 1);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && weaponName.equals(cs.getWeaponTitle(item))) {
                        if (i != targetSlot) {
                            inv.setItem(targetSlot, item);
                            inv.setItem(i, null);
                        }
                        inv.setHeldItemSlot(targetSlot);
                        break;
                    }
                }
                startStreakCounter(p, weaponName);
                startTimedWeaponChange(p, weaponName);
            }
        }.runTaskLater(plugin, 1L);
    }
}