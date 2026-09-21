package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponRecoveryStore;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class WeaponsSPMode implements Listener {

    private final JavaPlugin plugin;
    private final CSUtility cs = new CSUtility();
    private final NamespacedKey weaponInstanceKey;
    private final WeaponRecoveryStore recoveryStore;

    // クールダウン管理
    private final Map<String, Long> cooldownMap = new HashMap<>();

    // キルストリーク管理（streakKey → count）
    private final Map<UUID, Map<String, Integer>> weaponKillStreakMap = new HashMap<>();

    // 武器変更中の個体管理（player UUID → weapon instance IDs）
    private final Map<UUID, Set<String>> changingWeaponInstances = new HashMap<>();

    private final Map<UUID, String> originalWeaponMap = new HashMap<>();
    private final Map<UUID, String> changedWeaponMap = new HashMap<>();

    private final Map<UUID, String> killStreakOriginalWeaponMap = new HashMap<>();
    private final Map<UUID, String> killStreakChangedWeaponMap = new HashMap<>();

    // キルストリークカウンター表示タスク
    private final Map<UUID, BukkitRunnable> counterTaskMap = new HashMap<>();

    // 時間経過武器変更タスク
    private final Map<UUID, BukkitRunnable> timedWeaponChangeTaskMap = new HashMap<>();

    // 時間経過武器変更の期限（プレイヤーごとに対象武器の個体IDと期限tickを保持）
    private final Map<UUID, TimedWeaponChangeState> timedWeaponChangeStateMap = new HashMap<>();

    // 時間経過武器変更のDelay_Barタスク
    private final Map<UUID, BukkitRunnable> timedDelayBarTaskMap = new HashMap<>();

    // Join_Changeの遅延インベントリ同期対策タスク
    private final Map<UUID, Set<BukkitRunnable>> joinChangeTaskMap = new HashMap<>();

    // アクションバー一時停止管理（残りtick数）
    private final Map<UUID, Integer> actionBarPauseMap = new HashMap<>();

    // ジャンプ状態管理
    private final Map<UUID, Boolean> jumpMap = new HashMap<>();
    private final Map<UUID, Double> lastYMap = new HashMap<>();

    //Othor
    private final Map<UUID, BukkitRunnable> weaponReturnCooldownBarTaskMap = new HashMap<>();
    private final AugActionBarManager actionBarManager;

    private static final class TimedWeaponChangeState {
        private final String instanceId;
        private final String weaponTitle;
        private final int totalTicks;
        private final long deadlineTick;

        private TimedWeaponChangeState(String instanceId, String weaponTitle, int totalTicks, long deadlineTick) {
            this.instanceId = instanceId;
            this.weaponTitle = weaponTitle;
            this.totalTicks = totalTicks;
            this.deadlineTick = deadlineTick;
        }
    }

    public WeaponsSPMode(AgetarouUniqueGuns plugin) {
        this.plugin = plugin;
        this.weaponInstanceKey = new NamespacedKey(plugin, "weapon_instance");
        this.recoveryStore = plugin.getWeaponRecoveryStore();
        this.actionBarManager = new AugActionBarManager(plugin, WeaponsSPMode::colorize);
    }

    /**
     * &x&R&R&G&G&B&B 形式および §x§R§R§G§G§B§B 形式の16進数カラーコードを変換してから
     * 通常の &a 等も変換する
     */
    public static String colorize(String text) {
        if (text == null) return "";
        text = convertHex(text, '&');
        text = convertHex(text, '\u00A7');
        text = ChatColor.translateAlternateColorCodes('&', text);
        return text;
    }


    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString();
    }

    private static String convertHex(String text, char p) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 13 < text.length()
                    && text.charAt(i) == p
                    && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X')
                    && isHexBlock(text, i + 2, p)) {
                StringBuilder hex = new StringBuilder();
                for (int j = 0; j < 6; j++) hex.append(text.charAt(i + 3 + j * 2));
                try {
                    out.append(net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
                    i += 14;
                    continue;
                } catch (Exception ignored) {}
            }
            out.append(text.charAt(i++));
        }
        return out.toString();
    }

    private static boolean isHexBlock(String text, int start, char p) {
        for (int j = 0; j < 6; j++) {
            int pos = start + j * 2;
            if (pos + 1 >= text.length()) return false;
            if (text.charAt(pos) != p) return false;
            char c = text.charAt(pos + 1);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
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
        if (root == null) return;

        event.setCancelled(true);

        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");

        if (p.isSneaking()
                && changeSection != null
                && changeSection.getBoolean("Enable", false)) {
            String target = changeSection.getString("Off_And_Shift");
            if (target != null && !target.isEmpty()) {
                handleWeaponChange(p, weaponTitle, "Off_And_Shift");
                return;
            }
        }

        ConfigurationSection killStreakSection = root.getConfigurationSection("KillStreak");
        if (killStreakSection == null || !killStreakSection.getBoolean("Enable", false)) return;

        ConfigurationSection eventSection = killStreakSection.getConfigurationSection("Streak_Event");
        if (eventSection == null || !eventSection.getBoolean("Enable", false)) return;

        if (!hasOffhandTrigger(eventSection)) return;

        String triggerAction = p.isSneaking() ? "shift_and_offhand" : "offhand";

        int currentStreak = getWeaponKillStreak(p, weaponTitle);
        checkStreakEvents(p, weaponTitle, currentStreak, triggerAction);
    }
    private boolean hasTriggerAction(ConfigurationSection eventSection, String targetAction) {
        for (ConfigurationSection costSec : getCostSections(eventSection)) {
            String actions = costSec.getString("Trigger_Action", "").trim().toLowerCase();
            // 完全一致で先に確認
            if (actions.equals(targetAction.toLowerCase())) return true;
            // 単体アクションの場合のみsplitして比較
            if (!targetAction.contains(",")) {
                for (String action : actions.split(",")) {
                    if (action.trim().equalsIgnoreCase(targetAction)) return true;
                }
            }
        }
        return false;
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

        restoreChangedWeaponOnDeath(p, changedWeaponMap.remove(uuid), originalWeaponMap.remove(uuid));
        restoreChangedWeaponOnDeath(p, killStreakChangedWeaponMap.remove(uuid), killStreakOriginalWeaponMap.remove(uuid));

        stopStreakCounter(p);
        TimedWeaponChangeState timedState = timedWeaponChangeStateMap.get(uuid);
        if (timedState != null && !shouldRestoreOnDeath(timedState.weaponTitle)) {
            pauseTimedWeaponChange(p);
        } else {
            stopTimedWeaponChange(p);
        }
        stopTimedDelayBar(p);
        jumpMap.remove(uuid);
        lastYMap.remove(uuid);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;

                TimedWeaponChangeState state = timedWeaponChangeStateMap.get(p.getUniqueId());
                if (state == null) return;

                ItemStack item = findWeaponByInstanceId(p, state.weaponTitle, state.instanceId);
                if (item == null) {
                    stopTimedWeaponChange(p);
                    stopTimedDelayBar(p);
                    return;
                }

                resumeTimedWeaponChange(p, state);
            }
        }.runTaskLater(plugin, 1L);
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
        changingWeaponInstances.remove(uuid);

        BukkitRunnable counterTask = counterTaskMap.remove(uuid);
        if (counterTask != null) counterTask.cancel();

        BukkitRunnable timedTask = timedWeaponChangeTaskMap.remove(uuid);
        if (timedTask != null) timedTask.cancel();
        timedWeaponChangeStateMap.remove(uuid);

        BukkitRunnable delayBarTask = timedDelayBarTaskMap.remove(uuid);
        if (delayBarTask != null) delayBarTask.cancel();

        stopJoinChangeTasks(uuid);

        BukkitRunnable returnCooldownTask = weaponReturnCooldownBarTaskMap.remove(uuid);
        if (returnCooldownTask != null) returnCooldownTask.cancel();
        actionBarManager.stop(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        stopJoinChangeTasks(p.getUniqueId());
        for (Map.Entry<String, ConfigurationSection> entry : WeaponConfig.getWeaponConfigs().entrySet()) {
            String weaponTitle = entry.getKey();
            ConfigurationSection root = entry.getValue();
            if (root == null) continue;

            ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
            if (changeSection == null || !changeSection.getBoolean("Enable", false)) continue;

            ConfigurationSection joinSection = changeSection.getConfigurationSection("Join_Change");
            if (joinSection == null || !joinSection.getBoolean("Enable", false)) continue;

            String targetWeapon = joinSection.getString("Target_Weapon");
            if (targetWeapon == null || targetWeapon.isEmpty() || weaponTitle.equals(targetWeapon)) continue;

            boolean takeoverAmmo = joinSection.getBoolean("Takeover_Ammo",
                    changeSection.getBoolean("Takeover_Ammo", false));
            int retryIntervalTicks = Math.max(1, joinSection.getInt("Retry_Interval_Ticks", 10));
            int retryCount = Math.max(1, joinSection.getInt("Retry_Count", 10));

            startJoinChangeRetry(
                    p,
                    weaponTitle,
                    targetWeapon,
                    takeoverAmmo,
                    retryIntervalTicks,
                    retryCount
            );
        }
    }

    private void startJoinChangeRetry(Player p, String weaponTitle, String targetWeapon,
                                      boolean takeoverAmmo, int retryIntervalTicks, int retryCount) {
        UUID uuid = p.getUniqueId();
        BukkitRunnable task = new BukkitRunnable() {
            private int attempts;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    finishJoinChangeTask(uuid, this);
                    return;
                }

                applyJoinWeaponChange(p, weaponTitle, targetWeapon, takeoverAmmo);
                attempts++;

                if (attempts >= retryCount) {
                    finishJoinChangeTask(uuid, this);
                }
            }
        };

        joinChangeTaskMap.computeIfAbsent(uuid, ignored -> new HashSet<>()).add(task);
        task.runTaskTimer(plugin, retryIntervalTicks, retryIntervalTicks);
    }

    private void applyJoinWeaponChange(Player p, String weaponTitle, String targetWeapon, boolean takeoverAmmo) {
        PlayerInventory inv = p.getInventory();
        int storageSize = inv.getStorageContents().length;

        for (int slot = 0; slot < storageSize; slot++) {
            ItemStack item = inv.getItem(slot);
            if (!weaponTitle.equals(cs.getWeaponTitle(item))) continue;

            if (hasConvertedWeaponInstance(p, item, targetWeapon)) {
                inv.setItem(slot, null);
                continue;
            }

            replaceWeaponInSlot(p, weaponTitle, targetWeapon, slot, takeoverAmmo);
        }

        ItemStack offhand = inv.getItemInOffHand();
        if (weaponTitle.equals(cs.getWeaponTitle(offhand))) {
            if (hasConvertedWeaponInstance(p, offhand, targetWeapon)) {
                inv.setItemInOffHand(null);
            } else {
                replaceWeaponInOffHand(p, weaponTitle, targetWeapon, takeoverAmmo);
            }
        }

        ItemStack cursor = p.getItemOnCursor();
        if (weaponTitle.equals(cs.getWeaponTitle(cursor))) {
            if (hasConvertedWeaponInstance(p, cursor, targetWeapon)) {
                p.setItemOnCursor(null);
            } else {
                replaceWeaponOnCursor(p, weaponTitle, targetWeapon, takeoverAmmo);
            }
        }
    }

    private boolean hasConvertedWeaponInstance(Player p, ItemStack sourceItem, String targetWeapon) {
        String instanceId = ensureWeaponInstanceId(sourceItem);
        return instanceId != null && findWeaponByInstanceId(p, targetWeapon, instanceId) != null;
    }

    private void finishJoinChangeTask(UUID uuid, BukkitRunnable task) {
        task.cancel();

        Set<BukkitRunnable> tasks = joinChangeTaskMap.get(uuid);
        if (tasks == null) return;

        tasks.remove(task);
        if (tasks.isEmpty()) joinChangeTaskMap.remove(uuid);
    }

    private void stopJoinChangeTasks(UUID uuid) {
        Set<BukkitRunnable> tasks = joinChangeTaskMap.remove(uuid);
        if (tasks == null) return;

        for (BukkitRunnable task : tasks) {
            task.cancel();
        }
    }

    // ===== WhenChangeWeapon =====

    private void handleWeaponChange(Player p, String currentWeapon, String triggerType) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) return;

        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null || !changeSection.getBoolean("Enable", false)) return;

        String targetWeapon = changeSection.getString(triggerType);
        if (targetWeapon == null || targetWeapon.isEmpty()) return;

        String returnCdKey = p.getUniqueId().toString() + "_ReturnChange_" + currentWeapon + "_" + triggerType;
        if (cooldownMap.getOrDefault(returnCdKey, 0L) > System.currentTimeMillis()) {
            ConfigurationSection returnCdSection = changeSection.getConfigurationSection("Return_Cooldown");
            if (returnCdSection != null) {
                String notReady = returnCdSection.getString("NotReady_Actionbar");
                if (notReady != null && !notReady.isEmpty()) {
                    actionBarPauseMap.put(p.getUniqueId(), 20);
                    actionBarManager.send(
                            p,
                            notReady,
                            20,
                            AugActionBarManager.PRIORITY_MESSAGE
                    );
                }
            }
            return;
        }

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
        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) return;

        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null) return;

        boolean takeoverAmmo = changeSection.getBoolean(triggerType + "_Takeover_Ammo", false);

        trackWeaponChange(p, currentWeapon, targetWeapon, false);
        replaceWeapon(p, currentWeapon, targetWeapon, takeoverAmmo);
        startReturnCooldown(p, currentWeapon, targetWeapon, changeSection);

        String sound = changeSection.getString("Sound");
        if (sound != null && !sound.isEmpty()) handleFeedbackSound(p, sound);

        int cooldownTicks = changeSection.getInt("CoolDown", 0);
        if (cooldownTicks > 0) {
            String cdKey = p.getUniqueId().toString() + "_WeaponChange_" + triggerType;
            cooldownMap.put(cdKey, System.currentTimeMillis() + (cooldownTicks * 50L));
        }

        String message = changeSection.getString("Message");
        if (message != null && !message.isEmpty()) {
            actionBarPauseMap.put(p.getUniqueId(), 40);
            actionBarManager.send(p, message, 40, AugActionBarManager.PRIORITY_MESSAGE);
        }

        sendTitleSubtitle(p, changeSection);
    }

    // ===== KillStreak =====
    private void addWeaponKillStreak(Player p, String weaponTitle) {
        ItemStack item = p.getInventory().getItemInMainHand();
        UUID uuid = p.getUniqueId();
        Map<String, Integer> playerStreaks = weaponKillStreakMap.computeIfAbsent(uuid, k -> new HashMap<>());

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null || !ks.getBoolean("Enable", false)) return;

        String key = getStreakStorageKey(item, weaponTitle);
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

    private int getWeaponKillStreak(Player p, String weaponTitle) {
        Map<String, Integer> streaks = weaponKillStreakMap.get(p.getUniqueId());
        if (streaks == null) return 0;

        ItemStack item = p.getInventory().getItemInMainHand();
        return streaks.getOrDefault(getStreakStorageKey(item, weaponTitle), 0);
    }

    private boolean consumeWeaponKillStreak(Player p, String weaponTitle, int amount) {
        Map<String, Integer> streaks = weaponKillStreakMap.get(p.getUniqueId());
        if (streaks == null) return false;

        ItemStack item = p.getInventory().getItemInMainHand();
        String key = getStreakStorageKey(item, weaponTitle);
        int current = streaks.getOrDefault(key, 0);
        if (current < amount) return false;

        streaks.put(key, current - amount);
        return true;
    }

    /**
     * Streak_Event.Add_Weapon_Streak により、現在の武器個体へ指定武器のストリークを加算する。
     * 設定例:
     * Add_Weapon_Streak:
     *   Weapon: TargetWeapon
     *   Amount: 1
     */
    private void addConfiguredWeaponStreak(Player p, String sourceWeapon,
                                           ConfigurationSection eventConfig) {
        ConfigurationSection addSection = eventConfig.getConfigurationSection("Add_Weapon_Streak");
        if (addSection == null) return;

        String targetWeapon = addSection.getString("Weapon");
        int amount = addSection.getInt("Amount", 1);
        if (targetWeapon == null || targetWeapon.isEmpty() || amount <= 0) return;

        ItemStack sourceItem = p.getInventory().getItemInMainHand();
        if (sourceItem == null || !sourceWeapon.equals(cs.getWeaponTitle(sourceItem))) return;

        String instanceId = ensureWeaponInstanceId(sourceItem);
        if (instanceId == null) return;

        ConfigurationSection targetRoot = WeaponConfig.getWeaponConfig(targetWeapon);
        if (targetRoot == null) return;
        ConfigurationSection targetStreak = targetRoot.getConfigurationSection("KillStreak");
        if (targetStreak == null || !targetStreak.getBoolean("Enable", false)) return;

        String storageKey = instanceId + ":" + getStreakKey(targetWeapon);
        Map<String, Integer> playerStreaks = weaponKillStreakMap.computeIfAbsent(
                p.getUniqueId(), ignored -> new HashMap<>());

        int current = playerStreaks.getOrDefault(storageKey, 0);
        int updated = current + amount;
        if (!targetStreak.getBoolean("Ignore_Limit", false)) {
            updated = Math.min(updated, targetStreak.getInt("Kill_Count", 5));
        }
        playerStreaks.put(storageKey, updated);

        if (sourceWeapon.equals(targetWeapon)) {
            startStreakCounter(p, sourceWeapon);
        }
    }

    /**
     * Restore_Ammo で指定数を回復し、Fill_Ammo が true なら最大装弾数まで回復する。
     * Shoot.Capacity を最大装弾数として扱い、未設定時のみ Reload.Reload_Amount を使用する。
     */
    private void restoreConfiguredWeaponAmmo(Player p, String weaponTitle,
                                             ConfigurationSection eventConfig) {
        boolean fillAmmo = eventConfig.getBoolean("Fill_Ammo", false);
        int restoreAmount = eventConfig.getInt("Restore_Ammo", 0);
        if (!fillAmmo && restoreAmount <= 0) return;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !weaponTitle.equals(cs.getWeaponTitle(item))) return;

        int maxAmmo = getWeaponMaxAmmo(weaponTitle);
        if (maxAmmo <= 0) return;

        int currentAmmo = getWeaponAmmoFromItem(p, weaponTitle, item);
        if (currentAmmo < 0) return;

        int restoredAmmo = fillAmmo
                ? maxAmmo
                : (int) Math.min((long) maxAmmo, (long) currentAmmo + restoreAmount);
        try {
            API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(restoredAmmo), weaponTitle);
        } catch (Exception ignored) {}
    }

    /**
     * 弾薬回復が設定されたイベントは、現在弾数を取得できて上限未満の場合だけ発動できる。
     * Restore_Ammo と Fill_Ammo がない既存イベントには影響しない。
     */
    private boolean canRestoreConfiguredWeaponAmmo(Player p, String weaponTitle,
                                                    ConfigurationSection eventConfig) {
        boolean fillAmmo = eventConfig.getBoolean("Fill_Ammo", false);
        if (!fillAmmo && !eventConfig.contains("Restore_Ammo")) return true;

        int restoreAmount = eventConfig.getInt("Restore_Ammo", 0);
        if (!fillAmmo && restoreAmount <= 0) return false;

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !weaponTitle.equals(cs.getWeaponTitle(item))) return false;

        int maxAmmo = getWeaponMaxAmmo(weaponTitle);
        if (maxAmmo <= 0) return false;

        int currentAmmo = getWeaponAmmoFromItem(p, weaponTitle, item);
        return currentAmmo >= 0 && currentAmmo < maxAmmo;
    }

    private int getWeaponMaxAmmo(String weaponTitle) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return -1;
        return root.getInt("Shoot.Capacity", root.getInt("Reload.Reload_Amount", -1));
    }


    private String getStreakKey(String weaponTitle) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return weaponTitle;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null) return weaponTitle;
        String shareKey = ks.getString("Streak_Share_Key");
        return (shareKey != null && !shareKey.isEmpty()) ? shareKey : weaponTitle;
    }

    private String ensureWeaponInstanceId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        String title = cs.getWeaponTitle(item);
        if (title == null) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String id = meta.getPersistentDataContainer().get(weaponInstanceKey, PersistentDataType.STRING);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            meta.getPersistentDataContainer().set(weaponInstanceKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }

        return id;
    }

    private String getWeaponInstanceId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        return meta.getPersistentDataContainer().get(weaponInstanceKey, PersistentDataType.STRING);
    }

    private void setWeaponInstanceId(ItemStack item, String instanceId) {
        if (item == null || item.getType() == Material.AIR || instanceId == null || instanceId.isEmpty()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.getPersistentDataContainer().set(weaponInstanceKey, PersistentDataType.STRING, instanceId);
        item.setItemMeta(meta);
    }

    private boolean beginWeaponChange(Player p, String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) return false;

        Set<String> changingInstances = changingWeaponInstances.computeIfAbsent(
                p.getUniqueId(), ignored -> new HashSet<>());
        return changingInstances.add(instanceId);
    }

    private void finishWeaponChange(Player p, String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) return;

        UUID uuid = p.getUniqueId();
        Set<String> changingInstances = changingWeaponInstances.get(uuid);
        if (changingInstances == null) return;

        changingInstances.remove(instanceId);
        if (changingInstances.isEmpty()) {
            changingWeaponInstances.remove(uuid);
        }
    }

    private void restoreItemInSlot(Player p, int slot, ItemStack originalItem) {
        if (originalItem == null || originalItem.getType() == Material.AIR) return;

        PlayerInventory inv = p.getInventory();
        ItemStack current = inv.getItem(slot);
        if (current == null || current.getType() == Material.AIR) {
            inv.setItem(slot, originalItem.clone());
            return;
        }

        addOrStoreRecovery(p, originalItem);
    }

    private void restoreItemInOffHand(Player p, ItemStack originalItem) {
        if (originalItem == null || originalItem.getType() == Material.AIR) return;

        ItemStack current = p.getInventory().getItemInOffHand();
        if (current == null || current.getType() == Material.AIR) {
            p.getInventory().setItemInOffHand(originalItem.clone());
            return;
        }

        addOrStoreRecovery(p, originalItem);
    }

    private void restoreItemOnCursor(Player p, ItemStack originalItem) {
        if (originalItem == null || originalItem.getType() == Material.AIR) return;

        ItemStack current = p.getItemOnCursor();
        if (current == null || current.getType() == Material.AIR) {
            p.setItemOnCursor(originalItem.clone());
            return;
        }

        addOrStoreRecovery(p, originalItem);
    }

    private void addOrStoreRecovery(Player p, ItemStack item) {
        Map<Integer, ItemStack> leftovers = p.getInventory().addItem(item.clone());
        for (ItemStack leftover : leftovers.values()) {
            recoveryStore.store(p, leftover, "weapon-change-rollback-inventory-full");
        }
    }

    private Map<Integer, Integer> snapshotWeaponAmounts(PlayerInventory inv, String weaponName) {
        Map<Integer, Integer> amounts = new HashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && weaponName.equals(cs.getWeaponTitle(item))) {
                amounts.put(i, item.getAmount());
            }
        }
        return amounts;
    }

    /**
     * giveWeapon 前後の個数差から、新たに生成された武器1個をインベントリから取り出す。
     * 同名武器へスタックされた場合も識別できる。
     */
    private ItemStack takeGeneratedWeapon(PlayerInventory inv, String weaponName,
                                          Map<Integer, Integer> amountsBefore) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || !weaponName.equals(cs.getWeaponTitle(item))) continue;

            int beforeAmount = amountsBefore.getOrDefault(i, 0);
            if (item.getAmount() <= beforeAmount) continue;

            ItemStack generated = item.clone();
            generated.setAmount(1);

            if (item.getAmount() == 1) {
                inv.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - 1);
                inv.setItem(i, item);
            }
            return generated;
        }
        return null;
    }

    private String getStreakStorageKey(ItemStack item, String weaponTitle) {
        String instanceId = ensureWeaponInstanceId(item);
        if (instanceId == null) {
            return getStreakKey(weaponTitle);
        }
        return instanceId + ":" + getStreakKey(weaponTitle);
    }





    private void handleDeathStreakReset(Player p) {
        Map<String, Integer> streaks = weaponKillStreakMap.get(p.getUniqueId());
        if (streaks == null) return;

        ItemStack currentItem = p.getInventory().getItemInMainHand();
        String currentWeapon = cs.getWeaponTitle(currentItem);
        if (currentWeapon == null) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) return;
        ConfigurationSection ks = root.getConfigurationSection("KillStreak");
        if (ks == null || !ks.getBoolean("Enable", false)) return;
        if (!ks.getBoolean("Remove_Streak", true)) return;

        String key = getStreakStorageKey(currentItem, currentWeapon);
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
                actionBarManager.send(p, display, 8, AugActionBarManager.PRIORITY_STREAK);
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
        actionBarManager.send(p, message, pauseTicks, AugActionBarManager.PRIORITY_MESSAGE);
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

        String maxAction = maxSec != null ? maxSec.getString("Trigger_Action", "") : "";
        String costAction = costSec != null ? costSec.getString("Trigger_Action", "") : "";

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
                    p.sendMessage(notEnoughChat);
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
        if (requiredActions == null || requiredActions.isEmpty()) return triggerAction == null;

        String normalized = requiredActions.trim().toLowerCase();
        String[] required = normalized.split(",");

        // 複数アクション指定 = AND条件（全部満たす必要あり）
        if (required.length > 1) {
            for (String action : required) {
                switch (action.trim()) {
                    case "offhand": if (!"offhand".equals(triggerAction) && !"shift_and_offhand".equals(triggerAction)) return false; break;
                    case "shift":   if (!"shift".equals(triggerAction) && !"shift_and_offhand".equals(triggerAction) && !p.isSneaking()) return false; break;
                    case "jump":    if (!jumpMap.getOrDefault(p.getUniqueId(), false)) return false; break;
                    default: return false;
                }
            }
            return true;
        }

        // 単体アクション指定
        switch (normalized) {
            case "offhand": return "offhand".equals(triggerAction);
            case "shift":   return "shift".equals(triggerAction);
            case "jump":    return "jump".equals(triggerAction) || jumpMap.getOrDefault(p.getUniqueId(), false);
            default:        return false;
        }
    }
    private boolean hasOffhandTrigger(ConfigurationSection eventSection) {
        for (ConfigurationSection costSec : getCostSections(eventSection)) {
            String actions = costSec.getString("Trigger_Action", "").trim().toLowerCase();
            if (actions.contains("offhand")) return true;
        }
        return false;
    }
    private void executeStreakEvent(Player p, String weaponTitle, ConfigurationSection eventConfig, boolean isMaxEvent, String triggerAction) {
        if (!canRestoreConfiguredWeaponAmmo(p, weaponTitle, eventConfig)) return;

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

        applyStreakEffects(p, eventConfig);
        addConfiguredWeaponStreak(p, weaponTitle, eventConfig);
        restoreConfiguredWeaponAmmo(p, weaponTitle, eventConfig);

        // 武器変更
        String changeWeapon = eventConfig.getString("Change_Weapons");
        if (changeWeapon != null && !changeWeapon.isEmpty()) {
            UUID uuid = p.getUniqueId();

            if (!killStreakOriginalWeaponMap.containsKey(uuid)) {
                killStreakOriginalWeaponMap.put(uuid, weaponTitle);
            }
            trackWeaponChange(p, weaponTitle, changeWeapon, true);

            if (eventConfig.getBoolean("Takeover_Streak", false)) {
                int remaining = getWeaponKillStreak(p, weaponTitle);
                weaponKillStreakMap.computeIfAbsent(uuid, k -> new HashMap<>());
                ItemStack item = p.getInventory().getItemInMainHand();
                String instanceKey = ensureWeaponInstanceId(item);
                String storageKey = instanceKey != null
                        ? instanceKey + ":" + getStreakKey(changeWeapon)
                        : getStreakKey(changeWeapon);

                weaponKillStreakMap.computeIfAbsent(uuid, k -> new HashMap<>())
                        .put(storageKey, remaining);
            }

            boolean takeoverAmmo = eventConfig.getBoolean("Takeover_Ammo", false);

            if ("offhand".equals(triggerAction)) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p.isOnline()) {
                            replaceWeapon(p, weaponTitle, changeWeapon, takeoverAmmo);
                            startReturnCooldown(p, weaponTitle, changeWeapon, eventConfig);
                        }
                    }
                }.runTaskLater(plugin, 1L);
            } else {
                replaceWeapon(p, weaponTitle, changeWeapon, takeoverAmmo);
                startReturnCooldown(p, weaponTitle, changeWeapon, eventConfig);
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
            actionBarPauseMap.put(p.getUniqueId(), 40);
            actionBarManager.send(p, actionbarMsg, 40, AugActionBarManager.PRIORITY_MESSAGE);
        }

        // チャット
        String chatMsg = eventConfig.getString("Saychat");
        if (chatMsg != null && !chatMsg.isEmpty()) {
            p.sendMessage(colorize(chatMsg));
        }

        // Title / Subtitle
        sendTitleSubtitle(p, eventConfig);

        // 音
        String sound = eventConfig.getString("Sound");
        if (sound != null && !sound.isEmpty()) handleFeedbackSound(p, sound);
    }

    // ===== 時間経過武器変更 =====

    private void startTimedWeaponChange(Player p, String weaponTitle) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) {
            stopTimedWeaponChange(p);
            return;
        }
        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        if (changeSection == null || !changeSection.getBoolean("Enable", false)) {
            stopTimedWeaponChange(p);
            return;
        }
        ConfigurationSection timedSection = changeSection.getConfigurationSection("Timed_Change");
        if (timedSection == null) {
            stopTimedWeaponChange(p);
            return;
        }

        int delayTicks = timedSection.getInt("Delay_Ticks", 0);
        String targetWeapon = timedSection.getString("Target_Weapon");
        if (delayTicks <= 0 || targetWeapon == null || targetWeapon.isEmpty()) {
            stopTimedWeaponChange(p);
            return;
        }

        UUID uuid = p.getUniqueId();
        ItemStack timedWeapon = findWeaponByTitle(p, weaponTitle);
        String instanceId = ensureWeaponInstanceId(timedWeapon);
        if (instanceId == null) {
            stopTimedWeaponChange(p);
            return;
        }

        TimedWeaponChangeState currentState = timedWeaponChangeStateMap.get(uuid);
        TimedWeaponChangeState state;
        if (currentState != null
                && currentState.instanceId.equals(instanceId)
                && currentState.weaponTitle.equals(weaponTitle)) {
            state = currentState;
        } else {
            state = new TimedWeaponChangeState(
                    instanceId,
                    weaponTitle,
                    delayTicks,
                    getCurrentServerTick() + delayTicks
            );
        }

        pauseTimedWeaponChange(p);
        timedWeaponChangeStateMap.put(uuid, state);
        resumeTimedWeaponChange(p, state);
    }

    private void resumeTimedWeaponChange(Player p, TimedWeaponChangeState state) {
        UUID uuid = p.getUniqueId();
        ConfigurationSection root = WeaponConfig.getWeaponConfig(state.weaponTitle);
        if (root == null) {
            stopTimedWeaponChange(p);
            return;
        }

        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        ConfigurationSection timedSection = changeSection != null
                ? changeSection.getConfigurationSection("Timed_Change")
                : null;
        String targetWeapon = timedSection != null ? timedSection.getString("Target_Weapon") : null;
        if (timedSection == null || targetWeapon == null || targetWeapon.isEmpty()) {
            stopTimedWeaponChange(p);
            return;
        }

        long remainingTicks = Math.max(0L, state.deadlineTick - getCurrentServerTick());
        int elapsedTicks = (int) Math.max(0L, Math.min((long) state.totalTicks,
                (long) state.totalTicks - remainingTicks));

        ConfigurationSection delayBarSection = timedSection.getConfigurationSection("Delay_Bar");
        if (delayBarSection != null && delayBarSection.getBoolean("Enable", false)) {
            startTimedDelayBar(p, delayBarSection, state.totalTicks, elapsedTicks);
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    timedWeaponChangeTaskMap.remove(uuid, this);
                    return;
                }

                if (p.isDead()) {
                    timedWeaponChangeTaskMap.remove(uuid, this);
                    return;
                }

                PlayerInventory inv = p.getInventory();
                int targetSlot = findWeaponSlotByInstanceId(inv, state.weaponTitle, state.instanceId);
                boolean takeoverAmmo = timedSection.getBoolean("Takeover_Ammo", false);

                if (targetSlot < 0
                        && !isWeaponInOffHand(p, state.weaponTitle, state.instanceId)
                        && !isWeaponOnCursor(p, state.weaponTitle, state.instanceId)) {
                    timedWeaponChangeTaskMap.remove(uuid, this);
                    timedWeaponChangeStateMap.remove(uuid, state);
                    stopTimedDelayBar(p);
                    return;
                }

                timedWeaponChangeTaskMap.remove(uuid, this);
                timedWeaponChangeStateMap.remove(uuid, state);
                stopTimedDelayBar(p);

                trackTimedWeaponChange(p, state.weaponTitle, targetWeapon);

                if (targetSlot >= 0) {
                    replaceWeaponInSlot(p, state.weaponTitle, targetWeapon, targetSlot, takeoverAmmo);
                } else if (isWeaponInOffHand(p, state.weaponTitle, state.instanceId)) {
                    replaceWeaponInOffHand(p, state.weaponTitle, targetWeapon, takeoverAmmo);
                } else {
                    replaceWeaponOnCursor(p, state.weaponTitle, targetWeapon, takeoverAmmo);
                }

                String sound = timedSection.getString("Sound");
                if (sound != null && !sound.isEmpty()) handleFeedbackSound(p, sound);

                String message = timedSection.getString("Message");
                if (message != null && !message.isEmpty()) {
                    actionBarPauseMap.put(p.getUniqueId(), 40);
                    actionBarManager.send(p, message, 40, AugActionBarManager.PRIORITY_MESSAGE);
                }

            }
        };

        task.runTaskLater(plugin, remainingTicks);
        timedWeaponChangeTaskMap.put(uuid, task);
    }

    private void stopTimedWeaponChange(Player p) {
        pauseTimedWeaponChange(p);
        timedWeaponChangeStateMap.remove(p.getUniqueId());
    }

    private void pauseTimedWeaponChange(Player p) {
        BukkitRunnable task = timedWeaponChangeTaskMap.remove(p.getUniqueId());
        if (task != null) task.cancel();
    }

    // ===== Delay Bar =====

    private void startTimedDelayBar(Player p, ConfigurationSection sec, int ticks, int elapsedTicks) {
        stopTimedDelayBar(p);

        UUID uuid = p.getUniqueId();
        BukkitRunnable task = new BukkitRunnable() {
            int i = Math.max(0, Math.min(ticks, elapsedTicks));

            public void run() {
                if (!p.isOnline()) {
                    this.cancel();
                    timedDelayBarTaskMap.remove(uuid, this);
                    return;
                }

                actionBarPauseMap.put(uuid, 4);

                if (i >= ticks) {
                    String endMsg = sec.getString("End_Action_Bar");
                    if (endMsg != null && !endMsg.isEmpty()) {
                        actionBarPauseMap.put(uuid, 30);
                        actionBarManager.send(
                                p,
                                endMsg.replace("{time}", "0.0"),
                                30,
                                AugActionBarManager.PRIORITY_BAR
                        );
                    }

                    String endSound = sec.getString("End_Sound");
                    if (endSound != null && !endSound.isEmpty()) {
                        handleFeedbackSound(p, endSound);
                    }

                    this.cancel();
                    timedDelayBarTaskMap.remove(uuid, this);
                    return;
                }

                String actionStr = sec.getString("Action_Bar");
                if (actionStr != null && !actionStr.isEmpty()) {
                    String bar = buildBar((double) i / ticks, sec);
                    String remainingSeconds = formatRemainingSeconds(ticks - i);
                    actionBarManager.send(
                            p,
                            actionStr.replace("{bar}", bar).replace("{time}", remainingSeconds),
                            8,
                            AugActionBarManager.PRIORITY_BAR
                    );
                }

                i += 2;
            }
        };

        task.runTaskTimer(plugin, 0L, 2L);
        timedDelayBarTaskMap.put(uuid, task);
    }

    private void stopTimedDelayBar(Player p) {
        BukkitRunnable task = timedDelayBarTaskMap.remove(p.getUniqueId());
        if (task != null) task.cancel();
    }

    private String formatRemainingSeconds(int remainingTicks) {
        double seconds = Math.max(0, remainingTicks) / 20.0;
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private void startReturnCooldown(Player p, String fromWeapon, String toWeapon, ConfigurationSection sourceSection) {
        ConfigurationSection sec = sourceSection.getConfigurationSection("Return_Cooldown");
        if (sec == null || !sec.getBoolean("Enable", false)) return;

        int ticks = sec.getInt("Ticks", 0);
        if (ticks <= 0) return;

        UUID uuid = p.getUniqueId();

        ConfigurationSection toRoot = WeaponConfig.getWeaponConfig(toWeapon);
        if (toRoot == null) return;

        ConfigurationSection toChangeSection = toRoot.getConfigurationSection("WhenChangeWeapon");
        if (toChangeSection == null || !toChangeSection.getBoolean("Enable", false)) return;

        for (String key : toChangeSection.getKeys(false)) {
            if (key.equalsIgnoreCase("Enable")
                    || key.equalsIgnoreCase("Sound")
                    || key.equalsIgnoreCase("Message")
                    || key.equalsIgnoreCase("CoolDown")
                    || key.equalsIgnoreCase("Timed_Change")
                    || key.equalsIgnoreCase("Join_Change")
                    || key.endsWith("_Takeover_Ammo")
                    || key.equalsIgnoreCase("Return_Cooldown")) {
                continue;
            }

            String target = toChangeSection.getString(key);
            if (!fromWeapon.equals(target)) continue;

            String cdKey = uuid + "_ReturnChange_" + toWeapon + "_" + key;
            cooldownMap.put(cdKey, System.currentTimeMillis() + (ticks * 50L));
        }

        startReturnCooldownBar(p, sec, ticks, toWeapon);
    }
    private void startReturnCooldownBar(Player p, ConfigurationSection sec, int ticks, String weaponTitle) {
        UUID uuid = p.getUniqueId();

        BukkitRunnable oldTask = weaponReturnCooldownBarTaskMap.remove(uuid);
        if (oldTask != null) oldTask.cancel();

        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    weaponReturnCooldownBarTaskMap.remove(uuid);
                    return;
                }

                ItemStack current = p.getInventory().getItemInMainHand();
                String currentTitle = cs.getWeaponTitle(current);
                if (!weaponTitle.equals(currentTitle)) {
                    elapsed += 2;
                    if (elapsed >= ticks) {
                        cancel();
                        weaponReturnCooldownBarTaskMap.remove(uuid);
                    }
                    return;
                }

                actionBarPauseMap.put(uuid, 4);

                String action = sec.getString("Action_Bar");
                if (action != null && !action.isEmpty()) {
                    String bar = buildReturnCooldownBar((double) elapsed / ticks, sec);
                    actionBarManager.send(
                            p,
                            action.replace("{bar}", bar),
                            8,
                            AugActionBarManager.PRIORITY_BAR
                    );
                }

                elapsed += 2;
                if (elapsed >= ticks) {
                    String end = sec.getString("End_Action_Bar");
                    if (end != null && !end.isEmpty()) {
                        actionBarPauseMap.put(uuid, 30);
                        actionBarManager.send(p, end, 30, AugActionBarManager.PRIORITY_BAR);
                    }

                    String endSound = sec.getString("End_Sound");
                    if (endSound != null && !endSound.isEmpty()) {
                        handleFeedbackSound(p, endSound);
                    }

                    cancel();
                    weaponReturnCooldownBarTaskMap.remove(uuid);
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 2L);
        weaponReturnCooldownBarTaskMap.put(uuid, task);
    }
    private String buildReturnCooldownBar(double pct, ConfigurationSection sec) {
        int len = sec.getInt("Symbol_Amount", 15);
        int left = (int) (pct * len);

        String sym = sec.getString("Symbol", "|");
        String leftColor = sec.getString("Left_Color", "&a");
        String rightColor = sec.getString("Right_Color", "&7");

        return leftColor + repeat(sym, left) + rightColor + repeat(sym, len - left);
    }

    private String buildBar(double pct, ConfigurationSection sec) {
        int len = sec.getInt("Symbol_Amount", 15);
        int left = (int) (pct * len);
        String sym = sec.getString("Symbol", "|");

        return sec.getString("Left_Color", "&a")
                + repeat(sym, left)
                + sec.getString("Right_Color", "&c")
                + repeat(sym, len - left);
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

    private void trackTimedWeaponChange(Player p, String currentWeapon, String targetWeapon) {
        if (shouldRestoreOnDeath(currentWeapon)) {
            trackWeaponChange(p, currentWeapon, targetWeapon, false);
            return;
        }

        UUID uuid = p.getUniqueId();
        clearCompletedWeaponChange(
                uuid,
                currentWeapon,
                targetWeapon,
                originalWeaponMap,
                changedWeaponMap
        );
        clearCompletedWeaponChange(
                uuid,
                currentWeapon,
                targetWeapon,
                killStreakOriginalWeaponMap,
                killStreakChangedWeaponMap
        );
    }

    private void clearCompletedWeaponChange(UUID uuid, String currentWeapon, String targetWeapon,
                                            Map<UUID, String> originalMap, Map<UUID, String> changedMap) {
        if (!targetWeapon.equals(originalMap.get(uuid)) || !currentWeapon.equals(changedMap.get(uuid))) return;

        originalMap.remove(uuid);
        changedMap.remove(uuid);
    }

    private void restoreChangedWeaponOnDeath(Player p, String changedWeapon, String originalWeapon) {
        if (!shouldRestoreOnDeath(changedWeapon)) return;
        restoreChangedWeaponIfPresent(p, changedWeapon, originalWeapon);
    }

    private boolean shouldRestoreOnDeath(String weaponTitle) {
        if (weaponTitle == null || weaponTitle.isEmpty()) return true;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
        if (root == null) return true;

        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
        return changeSection == null || changeSection.getBoolean("Restore_On_Death", true);
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

                ItemStack currentItem = inv.getItem(slot);
                if (currentItem == null) return;
                ItemStack originalItem = currentItem.clone();

                String instanceId = ensureWeaponInstanceId(inv.getItem(slot));
                if (!beginWeaponChange(p, instanceId)) return;

                inv.setItem(slot, null);
                String restoreWeapon = getReturnBaseWeapon(changedWeapon, originalWeapon);
                Map<Integer, Integer> amountsBefore = snapshotWeaponAmounts(inv, restoreWeapon);

                try {
                    cs.giveWeapon(p, restoreWeapon, 1);
                } catch (Exception ex) {
                    restoreItemInSlot(p, slot, originalItem);
                    finishWeaponChange(p, instanceId);
                    plugin.getLogger().warning("Failed to restore weapon " + restoreWeapon
                            + " for " + p.getName() + ": " + ex.getMessage());
                    return;
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) {
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        ItemStack generated = takeGeneratedWeapon(inv, restoreWeapon, amountsBefore);
                        if (generated == null) {
                            restoreItemInSlot(p, slot, originalItem);
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        ItemStack current = inv.getItem(slot);

                        if (current != null && current.getType() != Material.AIR) {
                            restoreItemInSlot(p, slot, originalItem);
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        setWeaponInstanceId(generated, instanceId);
                        inv.setItem(slot, generated);
                        finishWeaponChange(p, instanceId);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }.runTaskLater(plugin, 1L);
    }

    private String getReturnBaseWeapon(String currentWeapon, String fallbackWeapon) {
        ConfigurationSection root = WeaponConfig.getWeaponConfig(currentWeapon);
        if (root == null) return fallbackWeapon;

        String baseWeapon = root.getString("Return_Base_Weapon");
        if (baseWeapon == null || baseWeapon.isEmpty()) return fallbackWeapon;

        return baseWeapon;
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

    private ItemStack findWeaponByTitle(Player p, String weaponTitle) {
        PlayerInventory inv = p.getInventory();
        int slot = findWeaponSlot(inv, weaponTitle);
        if (slot >= 0) return inv.getItem(slot);

        ItemStack offhand = inv.getItemInOffHand();
        if (weaponTitle.equals(cs.getWeaponTitle(offhand))) return offhand;

        ItemStack cursor = p.getItemOnCursor();
        if (weaponTitle.equals(cs.getWeaponTitle(cursor))) return cursor;

        return null;
    }

    private ItemStack findWeaponByInstanceId(Player p, String weaponTitle, String instanceId) {
        PlayerInventory inv = p.getInventory();
        int slot = findWeaponSlotByInstanceId(inv, weaponTitle, instanceId);
        if (slot >= 0) return inv.getItem(slot);

        ItemStack offhand = inv.getItemInOffHand();
        if (matchesWeaponInstance(offhand, weaponTitle, instanceId)) return offhand;

        ItemStack cursor = p.getItemOnCursor();
        if (matchesWeaponInstance(cursor, weaponTitle, instanceId)) return cursor;

        return null;
    }

    private int findWeaponSlotByInstanceId(PlayerInventory inv, String weaponTitle, String instanceId) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (matchesWeaponInstance(inv.getItem(i), weaponTitle, instanceId)) return i;
        }
        return -1;
    }

    private boolean matchesWeaponInstance(ItemStack item, String weaponTitle, String instanceId) {
        return item != null
                && item.getType() != Material.AIR
                && weaponTitle.equals(cs.getWeaponTitle(item))
                && instanceId.equals(getWeaponInstanceId(item));
    }

    private long getCurrentServerTick() {
        return Bukkit.getServer().getCurrentTick();
    }

    private int getWeaponAmmoInSlot(Player p, String weaponName, int slot) {
        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || !weaponName.equals(cs.getWeaponTitle(item))) return -1;

        try {
            return API.getCSDirector().getAmmoBetweenBrackets(p, weaponName, item);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void applyWeaponAmmoToSlot(Player p, String weaponName, int slot, int ammo) {
        if (ammo < 0) return;

        ItemStack item = p.getInventory().getItem(slot);
        if (item == null || !weaponName.equals(cs.getWeaponTitle(item))) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponName);
        int maxAmmo = root != null ? root.getInt("Reload.Reload_Amount", root.getInt("Shoot.Capacity", -1)) : -1;

        if (maxAmmo > 0) ammo = Math.min(ammo, maxAmmo);
        ammo = Math.max(0, ammo);

        try {
            API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(ammo), weaponName);
        } catch (Exception ignored) {}
    }

    private void replaceWeaponInSlot(Player p, String expectedWeapon, String targetWeapon, int targetSlot) {
        replaceWeaponInSlot(p, expectedWeapon, targetWeapon, targetSlot, false);
    }

    private void replaceWeaponInSlot(Player p, String expectedWeapon, String targetWeapon, int targetSlot, boolean takeoverAmmo) {
        if (!p.isOnline()) return;

        PlayerInventory inv = p.getInventory();
        ItemStack current = inv.getItem(targetSlot);
        String currentTitle = cs.getWeaponTitle(current);

        if (!expectedWeapon.equals(currentTitle)) return;

        int ammo = takeoverAmmo ? getWeaponAmmoInSlot(p, expectedWeapon, targetSlot) : -1;

        giveWeaponIntoSlot(p, targetWeapon, targetSlot, false, ammo, () -> {
            if (inv.getHeldItemSlot() == targetSlot) {
                startStreakCounter(p, targetWeapon);
            }
            startTimedWeaponChange(p, targetWeapon);
        });
    }

    private boolean isWeaponOnCursor(Player p, String weaponName) {
        ItemStack cursor = p.getItemOnCursor();
        return cursor != null
                && cursor.getType() != Material.AIR
                && weaponName.equals(cs.getWeaponTitle(cursor));
    }

    private boolean isWeaponOnCursor(Player p, String weaponName, String instanceId) {
        return matchesWeaponInstance(p.getItemOnCursor(), weaponName, instanceId);
    }

    private boolean isWeaponInOffHand(Player p, String weaponName) {
        ItemStack item = p.getInventory().getItemInOffHand();
        return item != null
                && item.getType() != Material.AIR
                && weaponName.equals(cs.getWeaponTitle(item));
    }

    private boolean isWeaponInOffHand(Player p, String weaponName, String instanceId) {
        return matchesWeaponInstance(p.getInventory().getItemInOffHand(), weaponName, instanceId);
    }
    private void replaceWeaponInOffHand(Player p, String expectedWeapon, String targetWeapon, boolean takeoverAmmo) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;

                ItemStack current = p.getInventory().getItemInOffHand();
                String currentTitle = cs.getWeaponTitle(current);
                if (!expectedWeapon.equals(currentTitle)) return;

                int ammo = takeoverAmmo ? getWeaponAmmoFromItem(p, expectedWeapon, current) : -1;
                ItemStack beforeOffhand = current.clone();
                String instanceId = ensureWeaponInstanceId(current);
                if (!beginWeaponChange(p, instanceId)) return;

                p.getInventory().setItemInOffHand(null);

                PlayerInventory inv = p.getInventory();
                Map<Integer, Integer> amountsBefore = snapshotWeaponAmounts(inv, targetWeapon);

                try {
                    cs.giveWeapon(p, targetWeapon, 1);
                } catch (Exception ex) {
                    restoreItemInOffHand(p, beforeOffhand);
                    finishWeaponChange(p, instanceId);
                    plugin.getLogger().warning("Failed to change offhand weapon to " + targetWeapon
                            + " for " + p.getName() + ": " + ex.getMessage());
                    return;
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) {
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        ItemStack generated = takeGeneratedWeapon(inv, targetWeapon, amountsBefore);
                        if (generated == null) {
                            restoreItemInOffHand(p, beforeOffhand);
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        ItemStack offhandNow = inv.getItemInOffHand();
                        if (offhandNow != null && offhandNow.getType() != Material.AIR) {
                            restoreItemInOffHand(p, beforeOffhand);
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        setWeaponInstanceId(generated, instanceId);
                        inv.setItemInOffHand(generated);
                        applyWeaponAmmoToOffHand(p, targetWeapon, ammo);
                        finishWeaponChange(p, instanceId);
                        startTimedWeaponChange(p, targetWeapon);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }.runTaskLater(plugin, 1L);
    }
    private void applyWeaponAmmoToOffHand(Player p, String weaponName, int ammo) {
        if (ammo < 0) return;

        ItemStack item = p.getInventory().getItemInOffHand();
        if (item == null || !weaponName.equals(cs.getWeaponTitle(item))) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponName);
        int maxAmmo = root != null ? root.getInt("Reload.Reload_Amount", root.getInt("Shoot.Capacity", -1)) : -1;

        if (maxAmmo > 0) ammo = Math.min(ammo, maxAmmo);
        ammo = Math.max(0, ammo);

        try {
            API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(ammo), weaponName);
            p.getInventory().setItemInOffHand(item);
        } catch (Exception ignored) {}
    }

    private void replaceWeaponOnCursor(Player p, String expectedWeapon, String targetWeapon, boolean takeoverAmmo) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;

                ItemStack cursor = p.getItemOnCursor();
                if (cursor == null || cursor.getType() == Material.AIR) return;

                String cursorTitle = cs.getWeaponTitle(cursor);
                if (!expectedWeapon.equals(cursorTitle)) return;

                int ammo = takeoverAmmo ? getWeaponAmmoFromItem(p, expectedWeapon, cursor) : -1;
                ItemStack beforeCursor = cursor.clone();
                String instanceId = ensureWeaponInstanceId(cursor);
                if (!beginWeaponChange(p, instanceId)) return;

                p.setItemOnCursor(null);

                PlayerInventory inv = p.getInventory();
                Map<Integer, Integer> amountsBefore = snapshotWeaponAmounts(inv, targetWeapon);

                try {
                    cs.giveWeapon(p, targetWeapon, 1);
                } catch (Exception ex) {
                    restoreItemOnCursor(p, beforeCursor);
                    finishWeaponChange(p, instanceId);
                    plugin.getLogger().warning("Failed to change cursor weapon to " + targetWeapon
                            + " for " + p.getName() + ": " + ex.getMessage());
                    return;
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) {
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        ItemStack generated = takeGeneratedWeapon(inv, targetWeapon, amountsBefore);
                        if (generated == null) {
                            restoreItemOnCursor(p, beforeCursor);
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        ItemStack currentCursor = p.getItemOnCursor();
                        if (currentCursor != null && currentCursor.getType() != Material.AIR) {
                            restoreItemOnCursor(p, beforeCursor);
                            finishWeaponChange(p, instanceId);
                            return;
                        }

                        setWeaponInstanceId(generated, instanceId);
                        p.setItemOnCursor(generated);
                        applyWeaponAmmoToCursor(p, targetWeapon, ammo);
                        finishWeaponChange(p, instanceId);
                        startTimedWeaponChange(p, targetWeapon);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }.runTaskLater(plugin, 1L);
    }

    private int getWeaponAmmoFromItem(Player p, String weaponName, ItemStack item) {
        if (item == null || !weaponName.equals(cs.getWeaponTitle(item))) return -1;

        try {
            return API.getCSDirector().getAmmoBetweenBrackets(p, weaponName, item);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void applyWeaponAmmoToCursor(Player p, String weaponName, int ammo) {
        if (ammo < 0) return;

        ItemStack item = p.getItemOnCursor();
        if (item == null || !weaponName.equals(cs.getWeaponTitle(item))) return;

        ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponName);
        int maxAmmo = root != null ? root.getInt("Reload.Reload_Amount", root.getInt("Shoot.Capacity", -1)) : -1;

        if (maxAmmo > 0) ammo = Math.min(ammo, maxAmmo);
        ammo = Math.max(0, ammo);

        try {
            API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(ammo), weaponName);
            p.setItemOnCursor(item);
        } catch (Exception ignored) {}
    }

    private void replaceWeapon(Player p, String expectedWeapon, String targetWeapon) {
        replaceWeapon(p, expectedWeapon, targetWeapon, false);
    }

    private void replaceWeapon(Player p, String expectedWeapon, String targetWeapon, boolean takeoverAmmo) {
        int replaceSlot = p.getInventory().getHeldItemSlot();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;

                PlayerInventory inv = p.getInventory();

                if (inv.getHeldItemSlot() != replaceSlot) return;

                int ammo = -1;

                if (expectedWeapon != null) {
                    ItemStack current = inv.getItem(replaceSlot);
                    String currentTitle = cs.getWeaponTitle(current);
                    if (!expectedWeapon.equals(currentTitle)) return;

                    if (takeoverAmmo) {
                        ammo = getWeaponAmmoInSlot(p, expectedWeapon, replaceSlot);
                    }
                }

                giveWeaponIntoSlot(p, targetWeapon, replaceSlot, true, ammo, () -> {
                    startStreakCounter(p, targetWeapon);
                    startTimedWeaponChange(p, targetWeapon);
                });
            }
        }.runTaskLater(plugin, 1L);
    }

    private void giveWeaponIntoSlot(Player p, String weaponName, int targetSlot,
                                    boolean selectTargetSlot, int takeoverAmmo,
                                    Runnable onSuccess) {
        if (!p.isOnline()) return;

        PlayerInventory inv = p.getInventory();
        ItemStack currentTarget = inv.getItem(targetSlot);
        ItemStack beforeTarget = currentTarget != null ? currentTarget.clone() : null;
        String instanceId = ensureWeaponInstanceId(currentTarget);
        boolean changeLocked = instanceId != null;

        if (changeLocked && !beginWeaponChange(p, instanceId)) return;
        if (instanceId == null) instanceId = UUID.randomUUID().toString();

        final String targetInstanceId = instanceId;
        inv.setItem(targetSlot, null);

        Map<Integer, Integer> amountsBefore = snapshotWeaponAmounts(inv, weaponName);

        try {
            cs.giveWeapon(p, weaponName, 1);
        } catch (Exception ex) {
            restoreItemInSlot(p, targetSlot, beforeTarget);
            if (changeLocked) finishWeaponChange(p, targetInstanceId);
            plugin.getLogger().warning("Failed to give weapon " + weaponName
                    + " to " + p.getName() + ": " + ex.getMessage());
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) {
                    if (changeLocked) finishWeaponChange(p, targetInstanceId);
                    return;
                }

                ItemStack generated = takeGeneratedWeapon(inv, weaponName, amountsBefore);
                if (generated == null) {
                    restoreItemInSlot(p, targetSlot, beforeTarget);
                    if (changeLocked) finishWeaponChange(p, targetInstanceId);
                    return;
                }

                ItemStack current = inv.getItem(targetSlot);
                if (current != null && current.getType() != Material.AIR) {
                    restoreItemInSlot(p, targetSlot, beforeTarget);
                    if (changeLocked) finishWeaponChange(p, targetInstanceId);
                    return;
                }

                setWeaponInstanceId(generated, targetInstanceId);
                inv.setItem(targetSlot, generated);
                applyWeaponAmmoToSlot(p, weaponName, targetSlot, takeoverAmmo);

                if (selectTargetSlot) {
                    inv.setHeldItemSlot(targetSlot);
                }

                if (changeLocked) finishWeaponChange(p, targetInstanceId);
                if (onSuccess != null) onSuccess.run();
            }
        }.runTaskLater(plugin, 1L);
    }

    private void applyStreakEffects(Player p, ConfigurationSection eventConfig) {
        applyPotionEffects(p, eventConfig.getConfigurationSection("Potion_Effects"));
        StreakParticleEffect.play(plugin, p, eventConfig.getConfigurationSection("Particle_Effect"));

        double heal = eventConfig.getDouble("Heal", 0.0);
        if (heal > 0.0) {
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + heal));
        }

        int food = eventConfig.getInt("Food", 0);
        if (food != 0) {
            p.setFoodLevel(Math.max(0, Math.min(20, p.getFoodLevel() + food)));
        }

        double selfDamage = eventConfig.getDouble("Self_Damage", 0.0);
        if (selfDamage > 0.0) {
            p.damage(selfDamage);
        }

        applyAreaPotionEffects(p, eventConfig.getConfigurationSection("Ally_Potion_Effects"), true);
        applyAreaPotionEffects(p, eventConfig.getConfigurationSection("Enemy_Potion_Effects"), false);
    }

    private void applyPotionEffects(LivingEntity entity, ConfigurationSection effects) {
        if (effects == null) return;

        for (String key : effects.getKeys(false)) {
            PotionEffectType type = PotionEffectType.getByName(key.toUpperCase());
            if (type == null) continue;

            ConfigurationSection sec = effects.getConfigurationSection(key);
            int duration = 100;
            int amplifier = 0;
            boolean ambient = false;
            boolean particles = true;
            boolean icon = true;

            if (sec != null) {
                duration = sec.getInt("Duration", duration);
                amplifier = sec.getInt("Amplifier", amplifier);
                ambient = sec.getBoolean("Ambient", ambient);
                particles = sec.getBoolean("Particles", particles);
                icon = sec.getBoolean("Icon", icon);
            }

            entity.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        }
    }

    private void applyAreaPotionEffects(Player p, ConfigurationSection section, boolean ally) {
        if (section == null) return;

        double radius = section.getDouble("Radius", 8.0);
        ConfigurationSection effects = section.getConfigurationSection("Effects");
        if (effects == null) return;

        for (org.bukkit.entity.Entity entity : p.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (!(entity instanceof Player)) continue;

            Player target = (Player) entity;
            if (target.equals(p)) continue;

            boolean sameTeam = isSameTeam(p, target);
            if (ally != sameTeam) continue;

            applyPotionEffects(target, effects);
        }
    }

    private boolean isSameTeam(Player a, Player b) {
        try {
            net.azisaba.lgw.core.util.BattleTeam teamA =
                    net.azisaba.lgw.core.LeonGunWar.getPlugin().getManager().getBattleTeam(a);
            net.azisaba.lgw.core.util.BattleTeam teamB =
                    net.azisaba.lgw.core.LeonGunWar.getPlugin().getManager().getBattleTeam(b);
            return teamA != null && teamA.equals(teamB);
        } catch (Exception ignored) {
            return false;
        }
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
            String t  = title    != null ? colorize(title)    : "";
            String st = subtitle != null ? colorize(subtitle) : "";
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
