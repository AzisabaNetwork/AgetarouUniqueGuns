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

    // キルストリーク管理（streakKey → count）
    private final Map<UUID, Map<String, Integer>> weaponKillStreakMap = new HashMap<>();

    private final Map<UUID, String> originalWeaponMap = new HashMap<>();
    private final Map<UUID, String> changedWeaponMap = new HashMap<>();

    private final Map<UUID, String> killStreakOriginalWeaponMap = new HashMap<>();
    private final Map<UUID, String> killStreakChangedWeaponMap = new HashMap<>();

    // キルストリークカウンター表示タスク
    private final Map<UUID, BukkitRunnable> counterTaskMap = new HashMap<>();

    // 時間経過武器変更タスク
    private final Map<UUID, BukkitRunnable> timedWeaponChangeTaskMap = new HashMap<>();

    // アクションバー一時停止管理（残りtick数）
    private final Map<UUID, Integer> actionBarPauseMap = new HashMap<>();

    // ジャンプ状態管理
    private final Map<UUID, Boolean> jumpMap = new HashMap<>();
    private final Map<UUID, Double> lastYMap = new HashMap<>();

    public WeaponsSPMode(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * &x&R&R&G&G&B&B 形式の16進数カラーコードを変換してから
     * 通常の &a 等も変換する
     */
    private String colorize(String text) {
        if (text == null) return "";
        // &x&R&R&G&G&B&B → BungeeCord ChatColor
        java.util.regex.Pattern hexPattern =
                java.util.regex.Pattern.compile("&x(&[0-9a-fA-F]){6}");
        java.util.regex.Matcher matcher = hexPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            // "&x&R&R&G&G&B&B" から "#RRGGBB" を取り出す
            String hex = matcher.group().replace("&", "").substring(1); // "RRGGBB"
            try {
                matcher.appendReplacement(sb,
                        net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
            } catch (Exception ignored) {
                matcher.appendReplacement(sb, matcher.group());
            }
        }
        matcher.appendTail(sb);
        // 通常の &a 等も変換
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // ===== イベントハンドラ =====

    // --- シフト武器変更 ---
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

    // --- 弾丸切れ武器変更 ---
    @EventHandler(priority = EventPriority.MONITOR)
    public void onShoot(WeaponShootEvent event) {
        Player p = event.getPlayer();
        String title = event.getWeaponTitle();
        if (title == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                ItemStack current = p.getInventory().getItemInMainHand();
                String currentTitle = cs.getWeaponTitle(current);
                if (!title.equals(currentTitle)) return;

                int currentAmmo = API.getCSDirector().getAmmoBetweenBrackets(p, title, current);
                ConfigurationSection config = WeaponConfig.getWeaponConfig(title);
                if (config != null) {
                    int magSize = config.getInt("Shoot.Capacity", 0);
                    if (magSize > 0 && currentAmmo <= 0) {
                        handleWeaponChange(p, title, "Empty_Ammo");
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    // --- オフハンドキー（Fキー）検知 ---
    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        ItemStack mainHandItem = p.getInventory().getItemInMainHand();
        String weaponTitle = cs.getWeaponTitle(mainHandItem);
        if (weaponTitle == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);

        // シフト＋Fキー → WhenChangeWeapon の Off_And_Shift
        if (p.isSneaking() && root != null) {
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

        // KillStreak の offhand処理
        if (root == null) return;
        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        if (killStreakSection == null || !killStreakSection.getBoolean("Enable", false)) return;

        ConfigurationSection eventSection = killStreakSection.getConfigurationSection("Streak_Event");
        if (eventSection == null || !eventSection.getBoolean("Enable", false)) return;

        boolean hasOffhandAction = false;
        for (ConfigurationSection costSec : getCostSections(eventSection)) {
            for (String action : costSec.getString("Change_Weapons_WithAction", "").split(",")) {
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

    // --- 武器持ち替え時のカウンター更新 ---
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        ItemStack newItem = p.getInventory().getItem(event.getNewSlot());
        String newTitle = cs.getWeaponTitle(newItem);

        if (newTitle != null) {
            startStreakCounter(p, newTitle);
            if (!timedWeaponChangeTaskMap.containsKey(p.getUniqueId())) {
                startTimedWeaponChange(p, newTitle);
            }
        } else {
            stopStreakCounter(p);
        }
    }

    // --- ジャンプ検出 ---
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        double currentY = event.getTo().getY();
        Double lastY = lastYMap.get(uuid);

        if (lastY != null && currentY > lastY && !p.isOnGround()) {
            jumpMap.put(uuid, true);

            if (p.isSneaking()) {
                ItemStack item = p.getInventory().getItemInMainHand();
                String title = cs.getWeaponTitle(item);
                if (title != null) handleWeaponChange(p, title, "Jump_And_Shift");
            }

            new BukkitRunnable() {
                @Override
                public void run() { jumpMap.remove(uuid); }
            }.runTaskLater(plugin, 20L);
        }

        lastYMap.put(uuid, currentY);
    }

    // --- プレイヤーキル ---
    @EventHandler
    public void onPlayerKill(PlayerKillEvent event) {
        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        String title = cs.getWeaponTitle(item);
        if (title == null) return;

        addWeaponKillStreak(p, title);

        checkWeaponChangeKillStreak(p, title);
    }

    // --- モブキル（プレイヤーキルは除外） ---
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // プレイヤーキルは onPlayerKill で処理するのでここでは除外
        if (event.getEntity() instanceof Player) return;

        if (!(event.getEntity().getKiller() instanceof Player)) return;
        Player p = event.getEntity().getKiller();
        ItemStack item = p.getInventory().getItemInMainHand();
        String title = cs.getWeaponTitle(item);
        if (title == null) return;

        if (shouldCountMobKill(title)) {
            addWeaponKillStreak(p, title);
        }
        checkWeaponChangeKillStreak(p, title);
    }

    // --- プレイヤー死亡 ---
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        UUID uuid = p.getUniqueId();

        handleDeathStreakReset(p);

        restoreChangedWeaponIfPresent(p, changedWeaponMap.remove(uuid), originalWeaponMap.remove(uuid));
        restoreChangedWeaponIfPresent(p, killStreakChangedWeaponMap.remove(uuid), killStreakOriginalWeaponMap.remove(uuid));

        stopStreakCounter(p);
        stopTimedWeaponChange(p);
        jumpMap.remove(uuid);
        lastYMap.remove(uuid);
    }

    // --- ログアウト時クリーンアップ ---
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        weaponKillStreakMap.remove(uuid);
        jumpMap.remove(uuid);
        lastYMap.remove(uuid);
        originalWeaponMap.remove(uuid);
        changedWeaponMap.remove(uuid);
        killStreakChangedWeaponMap.remove(uuid);
        killStreakOriginalWeaponMap.remove(uuid);
        actionBarPauseMap.remove(uuid);

        BukkitRunnable counterTask = counterTaskMap.remove(uuid);
        if (counterTask != null) counterTask.cancel();

        BukkitRunnable timedTask = timedWeaponChangeTaskMap.remove(uuid);
        if (timedTask != null) timedTask.cancel();
    }

    // ===== WhenChangeWeapon =====

    private void handleWeaponChange(Player p, String currentWeapon, String triggerType) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) return;

        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null || !changeSection.getBoolean("Enable", false)) return;

        String targetWeapon = changeSection.getString(triggerType);
        if (targetWeapon == null || targetWeapon.isEmpty()) return;

        if (!checkItemRequirements(p, changeSection, triggerType)) return;

        String cdKey = p.getUniqueId().toString() + "_WeaponChange_" + triggerType;
        if (cooldownMap.getOrDefault(cdKey, 0L) > System.currentTimeMillis()) return;

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
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            boolean matches = false;
            if (itemType != null) {
                try {
                    if (item.getType() == Material.valueOf(itemType.toUpperCase())) matches = true;
                } catch (IllegalArgumentException ignored) {}
            }
            if (!matches && itemName != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                if (ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals(itemName)) matches = true;
            }

            if (matches) {
                currentAmount += item.getAmount();
                if (currentAmount >= requiredAmount) return true;
            }
        }
        return false;
    }

    private void performWeaponChange(Player p, String currentWeapon, String targetWeapon, String triggerType) {
        trackWeaponChange(p, currentWeapon, targetWeapon, false);
        replaceWeapon(p, targetWeapon);

        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) return;
        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null) return;

        String sound = changeSection.getString("Sound");
        if (sound != null && !sound.isEmpty()) handleFeedbackSound(p, sound);

        int cooldownTicks = changeSection.getInt("CoolDown", 0);
        if (cooldownTicks > 0) {
            String cdKey = p.getUniqueId().toString() + "_WeaponChange_" + triggerType;
            cooldownMap.put(cdKey, System.currentTimeMillis() + (cooldownTicks * 50L));
        }

        String message = changeSection.getString("Message");
        if (message != null && !message.isEmpty()) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
        }

        sendTitleSubtitle(p, changeSection);
    }

    // ===== KillStreak =====

    private void addWeaponKillStreak(Player p, String weaponTitle) {
        UUID uuid = p.getUniqueId();
        Map<String, Integer> playerStreaks = weaponKillStreakMap.computeIfAbsent(uuid, k -> new HashMap<>());

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null || !ks.getBoolean("Enable", false)) return;

        String key = getStreakKey(weaponTitle);
        int current = playerStreaks.getOrDefault(key, 0);
        int max = ks.getInt("Kill_Count", 5);
        boolean ignoreLimit = ks.getBoolean("Ignore_Limit", false);

        if (ignoreLimit || current < max) {
            current++;
            playerStreaks.put(key, current);
            startStreakCounter(p, weaponTitle);
            checkStreakEvents(p, weaponTitle, current);
        }
    }

    private String getStreakKey(String weaponTitle) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return weaponTitle;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null) return weaponTitle;
        String shareKey = ks.getString("Streak_Share_Key");
        return (shareKey != null && !shareKey.isEmpty()) ? shareKey : weaponTitle;
    }

    private int getWeaponKillStreak(Player p, String weaponTitle) {
        Map<String, Integer> streaks = weaponKillStreakMap.get(p.getUniqueId());
        return streaks != null ? streaks.getOrDefault(getStreakKey(weaponTitle), 0) : 0;
    }

    private boolean consumeWeaponKillStreak(Player p, String weaponTitle, int amount) {
        Map<String, Integer> streaks = weaponKillStreakMap.get(p.getUniqueId());
        if (streaks == null) return false;
        String key = getStreakKey(weaponTitle);
        int current = streaks.getOrDefault(key, 0);
        if (current < amount) return false;
        streaks.put(key, current - amount);
        return true;
    }

    private void handleDeathStreakReset(Player p) {
        Map<String, Integer> streaks = weaponKillStreakMap.get(p.getUniqueId());
        if (streaks == null) return;

        String currentWeapon = cs.getWeaponTitle(p.getInventory().getItemInMainHand());
        if (currentWeapon == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) return;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null || !ks.getBoolean("Enable", false)) return;
        if (!ks.getBoolean("Remove_Streak", true)) return;

        String key = getStreakKey(currentWeapon);
        int removeAmount = ks.getInt("Remove_Several_Streak", -1);
        if (removeAmount == -1) {
            streaks.remove(key);
        } else {
            streaks.put(key, Math.max(0, streaks.getOrDefault(key, 0) - removeAmount));
        }
        stopStreakCounter(p);
    }

    private boolean shouldCountMobKill(String weaponTitle) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return false;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        return ks != null && ks.getBoolean("Enable", false) && ks.getBoolean("Accumulate_Streaks_Defeat_Mobs", false);
    }

    /** WhenChangeWeapon.Kill_Streak によるキルストリーク武器変更 */
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
                if (currentStreak == Integer.parseInt(streakStr)) {
                    String targetWeapon = killStreakSection.getString(streakStr);
                    if (targetWeapon != null && !targetWeapon.isEmpty()) {
                        performWeaponChange(p, title, targetWeapon, "Kill_Streak_" + streakStr);
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // ===== ストリークカウンター表示 =====

    private void startStreakCounter(Player p, String weaponTitle) {
        stopStreakCounter(p);

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null || !ks.getBoolean("Enable", false)) return;
        ConfigurationSection icon = ks.getConfigurationSection("Streak_Icon");
        if (icon == null || !icon.getBoolean("Enable", false)) return;

        String leftSymbol  = icon.getString("Left",  "&4&l▶ ");
        String rightSymbol = icon.getString("Right", "&7&l◀ ");
        String barFormat   = icon.getString("Bar");   // null なら従来の Left/Right 並べる形式
        int maxCount = ks.getInt("Kill_Count", 5);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }

                UUID uuid = p.getUniqueId();
                int pause = actionBarPauseMap.getOrDefault(uuid, 0);
                if (pause > 0) {
                    actionBarPauseMap.put(uuid, pause - 5);
                    return;
                }

                int current = getWeaponKillStreak(p, weaponTitle);
                String display = buildStreakCounter(current, maxCount, leftSymbol, rightSymbol, barFormat);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(colorize(display)));
            }
        };

        task.runTaskTimer(plugin, 0L, 5L);
        counterTaskMap.put(p.getUniqueId(), task);
    }

    private void stopStreakCounter(Player p) {
        BukkitRunnable task = counterTaskMap.remove(p.getUniqueId());
        if (task != null) task.cancel();
    }

    private String buildStreakCounter(int current, int max,
                                      String leftSymbol, String rightSymbol,
                                      String barFormat) {
        if (barFormat != null && !barFormat.isEmpty()) {
            // Bar形式: Left で貯まった分、Right で残りを表現
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < current; i++) bar.append(leftSymbol);
            for (int i = current; i < max; i++) bar.append(rightSymbol);
            return barFormat.replace("{bar}", bar.toString());
        }
        // 従来形式（Bar未設定時）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < current; i++) sb.append(leftSymbol);
        for (int i = current; i < max; i++) sb.append(rightSymbol);
        return sb.toString();
    }

    private void sendNotenoughActionbar(Player p, String message, int pauseTicks) {
        actionBarPauseMap.put(p.getUniqueId(), pauseTicks);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.translateAlternateColorCodes('&', message)));
    }

    // ===== ストリークイベント =====

    private void checkStreakEvents(Player p, String weaponTitle, int currentStreak) {
        checkStreakEvents(p, weaponTitle, currentStreak, null);
    }

    private void checkStreakEvents(Player p, String weaponTitle, int currentStreak, String triggerAction) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null) return;
        ConfigurationSection eventSection = ks.getConfigurationSection("Streak_Event");
        if (eventSection == null || !eventSection.getBoolean("Enable", false)) return;

        int maxCount = ks.getInt("Kill_Count", 5);
        boolean isMax = currentStreak >= maxCount;

        ConfigurationSection maxSec = eventSection.getConfigurationSection("Cost_MaxCount");
        ConfigurationSection costSec = eventSection.getConfigurationSection("Cost_Count");

        String maxAction = maxSec != null ? maxSec.getString("Change_Weapons_WithAction", "") : "";
        String costAction = costSec != null ? costSec.getString("Change_Weapons_WithAction", "") : "";

        // 満タン時 → Cost_MaxCount を発火
        if (isMax && maxSec != null && matchesAction(maxAction, triggerAction, p)) {
            executeStreakEvent(p, weaponTitle, maxSec, true, triggerAction);
            // アクションが同じなら Cost_Count は発火しない
            if (maxAction.equalsIgnoreCase(costAction)) return;
        }

        // Cost_Count（アクションが違えば満タン時でも発火）
        if (costSec != null) {
            if (!costAction.isEmpty() && triggerAction == null) return;
            int costAmount = costSec.getInt("Cost_Count", 1);

            if (currentStreak >= costAmount && matchesAction(costAction, triggerAction, p)) {
                executeStreakEvent(p, weaponTitle, costSec, false, triggerAction);
            } else if (triggerAction != null && currentStreak < costAmount) {
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

        // 満タン未満でoffhand → Cost_MaxCount の Notenough を表示
        if (!isMax && "offhand".equals(triggerAction) && maxSec != null) {
            String notEnoughActionbar = maxSec.getString("Notenough_Actionbar");
            if (notEnoughActionbar != null && !notEnoughActionbar.isEmpty()) {
                sendNotenoughActionbar(p, notEnoughActionbar, 40);
            }
        }
    }

    private boolean matchesAction(String requiredActions, String triggerAction, Player p) {
        if (requiredActions.isEmpty()) return triggerAction == null;
        for (String action : requiredActions.split(",")) {
            switch (action.trim().toLowerCase()) {
                case "offhand": if ("offhand".equals(triggerAction)) return true; break;
                case "shift":   if ("shift".equals(triggerAction) || p.isSneaking()) return true; break;
                case "jump":    if ("jump".equals(triggerAction) || jumpMap.getOrDefault(p.getUniqueId(), false)) return true; break;
            }
        }
        return false;
    }

    private void executeStreakEvent(Player p, String weaponTitle, ConfigurationSection eventConfig, boolean isMaxEvent, String triggerAction) {
        // 消費量の算出
        int consumeAmount;
        if (isMaxEvent) {
            ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
            consumeAmount = (root != null && root.contains("KillStreak"))
                    ? root.getConfigurationSection("KillStreak").getInt("Kill_Count", 5) : 5;
        } else {
            consumeAmount = eventConfig.getInt("Cost_Count", 1);
        }
        if (!consumeWeaponKillStreak(p, weaponTitle, consumeAmount)) return;

        // 武器変更
        String changeWeapon = eventConfig.getString("Change_Weapons");
        if (changeWeapon != null && !changeWeapon.isEmpty()) {
            UUID uuid = p.getUniqueId();
            if (!killStreakOriginalWeaponMap.containsKey(uuid)) {
                killStreakOriginalWeaponMap.put(uuid, weaponTitle);
                trackWeaponChange(p, weaponTitle, changeWeapon, true);
            }

            if (eventConfig.getBoolean("Takeover_Streak", false)) {
                int remaining = getWeaponKillStreak(p, weaponTitle);
                weaponKillStreakMap.computeIfAbsent(uuid, k -> new HashMap<>())
                        .put(getStreakKey(changeWeapon), remaining);
            }

            final String fw = changeWeapon;
            if ("offhand".equals(triggerAction)) {
                new BukkitRunnable() {
                    @Override public void run() { if (p.isOnline()) replaceWeapon(p, fw); }
                }.runTaskLater(plugin, 1L);
            } else {
                replaceWeapon(p, fw);
            }
        }

        // コマンド
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
        sendTitleSubtitle(p, eventConfig);

        // 音
        String sound = eventConfig.getString("Sound");
        if (sound != null && !sound.isEmpty()) handleFeedbackSound(p, sound);
    }

    // ===== 時間経過武器変更 =====

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

        // Delay_Bar を即時開始
        ConfigurationSection delayBarSection = timedSection.getConfigurationSection("Delay_Bar");
        if (delayBarSection != null && delayBarSection.getBoolean("Enable", false)) {
            startTimedDelayBar(p, delayBarSection, delayTicks);
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) { this.cancel(); return; }
                trackWeaponChange(p, weaponTitle, targetWeapon, false);
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

    private void stopTimedWeaponChange(Player p) {
        BukkitRunnable task = timedWeaponChangeTaskMap.remove(p.getUniqueId());
        if (task != null) task.cancel();
    }

    // ===== Delay Bar =====

    private void startTimedDelayBar(Player p, ConfigurationSection sec, int ticks) {
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

    private String buildBar(double pct, ConfigurationSection sec) {
        int len = sec.getInt("Symbol_Amount", 15);
        int left = (int) (pct * len);
        String sym = sec.getString("Symbol", "|");
        String leftColor = ChatColor.translateAlternateColorCodes('&', sec.getString("Left_Color", "&a"));
        String rightColor = ChatColor.translateAlternateColorCodes('&', sec.getString("Right_Color", "&c"));
        return leftColor + repeat(sym, left) + rightColor + repeat(sym, len - left);
    }

    // ===== ユーティリティ =====
    private void trackWeaponChange(Player p, String currentWeapon, String targetWeapon, boolean killStreakChange) {
        if (currentWeapon == null || targetWeapon == null || currentWeapon.isEmpty() || targetWeapon.isEmpty()) return;

        UUID uuid = p.getUniqueId();
        boolean returned = false;

        if (targetWeapon.equals(originalWeaponMap.get(uuid))) {
            originalWeaponMap.remove(uuid);
            changedWeaponMap.remove(uuid);
            returned = true;
        }

        if (targetWeapon.equals(killStreakOriginalWeaponMap.get(uuid))) {
            killStreakOriginalWeaponMap.remove(uuid);
            killStreakChangedWeaponMap.remove(uuid);
            returned = true;
        }

        if (returned) return;

        if (killStreakChange) {
            killStreakOriginalWeaponMap.putIfAbsent(uuid, currentWeapon);
            killStreakChangedWeaponMap.put(uuid, targetWeapon);
        } else {
            originalWeaponMap.putIfAbsent(uuid, currentWeapon);
            changedWeaponMap.put(uuid, targetWeapon);
        }
    }

    private void restoreChangedWeaponIfPresent(Player p, String changedWeapon, String originalWeapon) {
        if (changedWeapon == null || originalWeapon == null || changedWeapon.isEmpty() || originalWeapon.isEmpty()) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;

                PlayerInventory inv = p.getInventory();
                int slot = findWeaponSlot(inv, changedWeapon);
                if (slot < 0) return;

                inv.setItem(slot, null);
                cs.giveWeapon(p, originalWeapon, 1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) return;

                        int generatedSlot = findWeaponSlot(inv, originalWeapon);
                        if (generatedSlot < 0) return;

                        ItemStack generated = inv.getItem(generatedSlot);
                        ItemStack current = inv.getItem(slot);

                        if (current != null && current.getType() != Material.AIR) {
                            if (generatedSlot != slot) inv.setItem(generatedSlot, null);
                            return;
                        }

                        if (generatedSlot != slot) {
                            inv.setItem(slot, generated);
                            inv.setItem(generatedSlot, null);
                        }
                        inv.setHeldItemSlot(slot);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }.runTaskLater(plugin, 1L);
    }

    private int findWeaponSlot(PlayerInventory inv, String weaponName) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && weaponName.equals(cs.getWeaponTitle(item))) {
                return i;
            }
        }
        return -1;
    }

    private void replaceWeapon(Player p, String weaponName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                PlayerInventory inv = p.getInventory();
                int replaceSlot = inv.getHeldItemSlot();
                inv.setItem(replaceSlot, null);
                cs.giveWeapon(p, weaponName, 1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) return;
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

    private void handleFeedbackSound(Player p, String rawSounds) {
        for (String entry : rawSounds.split(",")) {
            String[] parts = entry.trim().split("-");
            if (parts.length == 0 || parts[0].isEmpty()) continue;
            try {
                Sound sound = Sound.valueOf(parts[0].toUpperCase());
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch  = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                int delay    = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
                if (delay <= 0) {
                    p.playSound(p.getLocation(), sound, volume, pitch);
                } else {
                    new BukkitRunnable() {
                        @Override public void run() { if (p.isOnline()) p.playSound(p.getLocation(), sound, volume, pitch); }
                    }.runTaskLater(plugin, delay);
                }
            } catch (Exception ignored) {}
        }
    }

    private void sendTitleSubtitle(Player p, ConfigurationSection config) {
        String title = config.getString("Title");
        String subtitle = config.getString("Subtitle");
        if (title != null || subtitle != null) {
            String t  = title    != null ? ChatColor.translateAlternateColorCodes('&', title)    : "";
            String st = subtitle != null ? ChatColor.translateAlternateColorCodes('&', subtitle) : "";
            p.sendTitle(t, st, 10, 70, 20);
        }
    }

    private java.util.List<ConfigurationSection> getCostSections(ConfigurationSection eventSection) {
        java.util.List<ConfigurationSection> list = new java.util.ArrayList<>();
        ConfigurationSection maxSec  = eventSection.getConfigurationSection("Cost_MaxCount");
        ConfigurationSection costSec = eventSection.getConfigurationSection("Cost_Count");
        if (maxSec  != null) list.add(maxSec);
        if (costSec != null) list.add(costSec);
        return list;
    }

    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(str);
        return sb.toString();
    }
}