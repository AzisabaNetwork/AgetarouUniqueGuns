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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

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

    //Othor
    private final Map<UUID, BukkitRunnable> weaponReturnCooldownBarTaskMap = new HashMap<>();
    private final AugActionBarManager actionBarManager;

    public WeaponsSPMode(JavaPlugin plugin) {
        this.plugin = plugin;
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

        BukkitRunnable returnCooldownTask = weaponReturnCooldownBarTaskMap.remove(uuid);
        if (returnCooldownTask != null) returnCooldownTask.cancel();
        actionBarManager.stop(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;

                PlayerInventory inv = p.getInventory();

                for (int slot = 0; slot < inv.getSize(); slot++) {
                    ItemStack item = inv.getItem(slot);
                    String weaponTitle = cs.getWeaponTitle(item);
                    if (weaponTitle == null) continue;

                    ConfigurationSection root = WeaponConfig.getWeaponConfig(weaponTitle);
                    if (root == null) continue;

                    ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
                    if (changeSection == null || !changeSection.getBoolean("Enable", false)) continue;

                    ConfigurationSection joinSection = changeSection.getConfigurationSection("Join_Change");
                    if (joinSection == null || !joinSection.getBoolean("Enable", false)) continue;

                    String targetWeapon = joinSection.getString("Target_Weapon");
                    if (targetWeapon == null || targetWeapon.isEmpty()) continue;

                    boolean takeoverAmmo = joinSection.getBoolean("Takeover_Ammo",
                            changeSection.getBoolean("Takeover_Ammo", false));

                    replaceWeaponInSlot(p, weaponTitle, targetWeapon, slot, takeoverAmmo);
                }
                ItemStack offhand = inv.getItemInOffHand();
                String offhandTitle = cs.getWeaponTitle(offhand);
                if (offhandTitle != null) {
                    ConfigurationSection root = WeaponConfig.getWeaponConfig(offhandTitle);
                    if (root != null) {
                        ConfigurationSection changeSection = root.getConfigurationSection("WhenChangeWeapon");
                        if (changeSection != null && changeSection.getBoolean("Enable", false)) {
                            ConfigurationSection joinSection = changeSection.getConfigurationSection("Join_Change");
                            if (joinSection != null && joinSection.getBoolean("Enable", false)) {
                                String targetWeapon = joinSection.getString("Target_Weapon");
                                if (targetWeapon != null && !targetWeapon.isEmpty()) {
                                    boolean takeoverAmmo = joinSection.getBoolean("Takeover_Ammo", false);
                                    replaceWeaponInOffHand(p, offhandTitle, targetWeapon, takeoverAmmo);
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 20L);
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
                weaponKillStreakMap.computeIfAbsent(uuid, k -> new HashMap<>())
                        .put(getStreakKey(changeWeapon), remaining);
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
                if (!p.isOnline()) {
                    this.cancel();
                    timedWeaponChangeTaskMap.remove(uuid);
                    return;
                }

                PlayerInventory inv = p.getInventory();
                int targetSlot = findWeaponSlot(inv, weaponTitle);
                boolean takeoverAmmo = timedSection.getBoolean("Takeover_Ammo", false);

                if (targetSlot < 0
                        && !isWeaponInOffHand(p, weaponTitle)
                        && !isWeaponOnCursor(p, weaponTitle)) {
                    this.cancel();
                    timedWeaponChangeTaskMap.remove(uuid);
                    return;
                }

                trackWeaponChange(p, weaponTitle, targetWeapon, false);

                if (targetSlot >= 0) {
                    replaceWeaponInSlot(p, weaponTitle, targetWeapon, targetSlot, takeoverAmmo);
                } else if (isWeaponInOffHand(p, weaponTitle)) {
                    replaceWeaponInOffHand(p, weaponTitle, targetWeapon, takeoverAmmo);
                } else {
                    replaceWeaponOnCursor(p, weaponTitle, targetWeapon, takeoverAmmo);
                }

                String sound = timedSection.getString("Sound");
                if (sound != null && !sound.isEmpty()) handleFeedbackSound(p, sound);

                String message = timedSection.getString("Message");
                if (message != null && !message.isEmpty()) {
                    actionBarPauseMap.put(p.getUniqueId(), 40);
                    actionBarManager.send(p, message, 40, AugActionBarManager.PRIORITY_MESSAGE);
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
                if (!p.isOnline()) {
                    this.cancel();
                    return;
                }

                UUID uuid = p.getUniqueId();
                actionBarPauseMap.put(uuid, 4);

                if (i >= ticks) {
                    String endMsg = sec.getString("End_Action_Bar");
                    if (endMsg != null && !endMsg.isEmpty()) {
                        actionBarPauseMap.put(uuid, 30);
                        actionBarManager.send(p, endMsg, 30, AugActionBarManager.PRIORITY_BAR);
                    }

                    String endSound = sec.getString("End_Sound");
                    if (endSound != null && !endSound.isEmpty()) {
                        handleFeedbackSound(p, endSound);
                    }

                    this.cancel();
                    return;
                }

                String actionStr = sec.getString("Action_Bar");
                if (actionStr != null && !actionStr.isEmpty()) {
                    String bar = buildBar((double) i / ticks, sec);
                    actionBarManager.send(
                            p,
                            actionStr.replace("{bar}", bar),
                            8,
                            AugActionBarManager.PRIORITY_BAR
                    );
                }

                i += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
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
                String restoreWeapon = getReturnBaseWeapon(changedWeapon, originalWeapon);
                cs.giveWeapon(p, restoreWeapon, 1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) return;

                        int generatedSlot = findWeaponSlot(inv, restoreWeapon);
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

        giveWeaponIntoSlot(p, targetWeapon, targetSlot, false, ammo);

        if (inv.getHeldItemSlot() == targetSlot) {
            startStreakCounter(p, targetWeapon);
            startTimedWeaponChange(p, targetWeapon);
        }
    }

    private boolean isWeaponOnCursor(Player p, String weaponName) {
        ItemStack cursor = p.getItemOnCursor();
        return cursor != null
                && cursor.getType() != Material.AIR
                && weaponName.equals(cs.getWeaponTitle(cursor));
    }
    private boolean isWeaponInOffHand(Player p, String weaponName) {
        ItemStack item = p.getInventory().getItemInOffHand();
        return item != null
                && item.getType() != Material.AIR
                && weaponName.equals(cs.getWeaponTitle(item));
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

                p.getInventory().setItemInOffHand(null);
                cs.giveWeapon(p, targetWeapon, 1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) return;

                        PlayerInventory inv = p.getInventory();
                        int generatedSlot = findWeaponSlot(inv, targetWeapon);

                        if (generatedSlot < 0) {
                            ItemStack offhandNow = inv.getItemInOffHand();
                            if (offhandNow == null || offhandNow.getType() == Material.AIR) {
                                inv.setItemInOffHand(beforeOffhand);
                            }
                            return;
                        }

                        ItemStack generated = inv.getItem(generatedSlot);
                        inv.setItem(generatedSlot, null);

                        inv.setItemInOffHand(generated);
                        applyWeaponAmmoToOffHand(p, targetWeapon, ammo);
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

                p.setItemOnCursor(null);
                cs.giveWeapon(p, targetWeapon, 1);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isOnline()) return;

                        PlayerInventory inv = p.getInventory();
                        int generatedSlot = findWeaponSlot(inv, targetWeapon);

                        if (generatedSlot < 0) {
                            ItemStack currentCursor = p.getItemOnCursor();
                            if (currentCursor == null || currentCursor.getType() == Material.AIR) {
                                p.setItemOnCursor(beforeCursor);
                            }
                            return;
                        }

                        ItemStack generated = inv.getItem(generatedSlot);
                        inv.setItem(generatedSlot, null);

                        p.setItemOnCursor(generated);
                        applyWeaponAmmoToCursor(p, targetWeapon, ammo);
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

    private void replaceWeapon(Player p, String weaponName) {
        replaceWeapon(p, null, weaponName, false);
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

                inv.setItem(replaceSlot, null);
                giveWeaponIntoSlot(p, targetWeapon, replaceSlot, true, ammo);

                startStreakCounter(p, targetWeapon);
                startTimedWeaponChange(p, targetWeapon);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void giveWeapon(Player p, String weaponName) {
        PlayerInventory inv = p.getInventory();
        giveWeaponIntoSlot(p, weaponName, inv.getHeldItemSlot());

        startStreakCounter(p, weaponName);
        startTimedWeaponChange(p, weaponName);
    }

    private void giveWeaponIntoSlot(Player p, String weaponName, int targetSlot) {
        giveWeaponIntoSlot(p, weaponName, targetSlot, true, -1);
    }

    private void giveWeaponIntoSlot(Player p, String weaponName, int targetSlot, boolean selectTargetSlot) {
        giveWeaponIntoSlot(p, weaponName, targetSlot, selectTargetSlot, -1);
    }

    private void giveWeaponIntoSlot(Player p, String weaponName, int targetSlot, boolean selectTargetSlot, int takeoverAmmo) {
        if (!p.isOnline()) return;

        PlayerInventory inv = p.getInventory();

        ItemStack beforeTarget = inv.getItem(targetSlot);
        inv.setItem(targetSlot, null);

        cs.giveWeapon(p, weaponName, 1);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;

                int generatedSlot = findWeaponSlot(inv, weaponName);
                if (generatedSlot < 0) {
                    if (inv.getItem(targetSlot) == null || inv.getItem(targetSlot).getType() == Material.AIR) {
                        inv.setItem(targetSlot, beforeTarget);
                    }
                    return;
                }

                ItemStack generated = inv.getItem(generatedSlot);

                if (generatedSlot != targetSlot) {
                    inv.setItem(generatedSlot, null);
                }

                inv.setItem(targetSlot, generated);
                applyWeaponAmmoToSlot(p, weaponName, targetSlot, takeoverAmmo);

                if (selectTargetSlot) {
                    inv.setHeldItemSlot(targetSlot);
                }
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