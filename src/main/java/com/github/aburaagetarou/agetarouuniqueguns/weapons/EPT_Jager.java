package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.CSDirector;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import com.shampaggon.crackshot.events.WeaponShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.azisaba.leoncsaddon.WeaponCustomDamageListener;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * EPT_Jager
 * 効果時間中、1Tick内に受けるダメージの回数を固定する
 * @author AburaAgeTarou
 */
public class EPT_Jager implements Listener {

	public final static String WEAPON_NAME = "EPT_Jager";

	public final static String KEY_DAMAGE_LIMIT_PER_TICK = "Damage_Limit_Per_Tick";
	public final static String KEY_EFFECT_DURATION = "Effect_Duration";

	// 初期設定
	private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{
		set(KEY_DAMAGE_LIMIT_PER_TICK, 3);
		set(KEY_EFFECT_DURATION, 240);
	}}};

	// 効果発動中のプレイヤー
	private static final Map<Entity, Integer> effectPlayers = new HashMap<>();

	// 被ダメージ数カウント
	private static final Map<Entity, Integer> damagedCount = new HashMap<>();

	private static BukkitTask timerTask;

	/**
	 * 被ダメージ数カウントリセットタイマー開始
	 */
	public static void initTimer() {
		timerTask = Bukkit.getScheduler().runTaskTimer(AgetarouUniqueGuns.getInstance(), damagedCount::clear, 0, 1);
	}

	/**
	 * タイマー停止
	 */
	public static void stopTimer() {
		if(timerTask != null) timerTask.cancel();
	}

	/**
	 * 設定情報の取得
	 * @param key キー
	 * @return Object 設定情報、存在しない場合はデフォルト設定
	 */
	public static Object getConfig(String key) {
		ConfigurationSection config = WeaponConfig.getWeaponConfig(WEAPON_NAME);
		if(config == null) {
			config = defaultConfig;
		}
		if(!config.contains(key)) {
			config = defaultConfig;
		}
		return config.get(key);
	}

	/**
	 * 設定から被ダメージ数を取得
	 * @return int 被ダメージ数
	 */
	public static int getDamageLimitPerTick() {
		try {
			return Integer.parseInt(getConfig(KEY_DAMAGE_LIMIT_PER_TICK).toString());
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_DAMAGE_LIMIT_PER_TICK);
			return defaultConfig.getInt(KEY_DAMAGE_LIMIT_PER_TICK);
		}
	}

	/**
	 * 設定から効果時間を取得
	 * @return int 効果時間(Tick)
	 */
	public static int getEffectDuration() {
		try {
			return Integer.parseInt(getConfig(KEY_EFFECT_DURATION).toString());
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_EFFECT_DURATION);
			return defaultConfig.getInt(KEY_EFFECT_DURATION);
		}
	}

	/**
	 * 効果発動
	 * @param player プレイヤー
	 */
	public static void apply(Player player) {
		String weaponTitle = API.getCSUtility().getWeaponTitle(player.getInventory().getItemInMainHand());
		if(weaponTitle == null) return;
		String orgWeaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
		if(!WEAPON_NAME.equals(orgWeaponTitle)) return;

		String weaponName = API.getCSDirector().getString(weaponTitle + ".Item_Information.Item_Name");

		int duration = getEffectDuration();
		effectPlayers.put(player, Bukkit.getServer().getCurrentTick() + duration);
		Bukkit.getScheduler().runTaskLater(AgetarouUniqueGuns.getInstance(), () -> {
			player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(weaponName + " §cの効果が切れました"));
		}, duration);
	}

	/**
	 * 武器使用
	 * @param event 武器使用イベント
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWeaponShoot(WeaponShootEvent event) {
		String weaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
		if(!WEAPON_NAME.equals(weaponTitle)) return;

		apply(event.getPlayer());
	}

	/**
	 * 被ダメージ
	 * @param event 被ダメージイベント
	 */
	@EventHandler
	public void onDamage(WeaponDamageEntityEvent event) {
		Entity victim = event.getVictim();

		if(effectPlayers.getOrDefault(victim, 0) > Bukkit.getServer().getCurrentTick()) {
			int count = EPT_Jager.damagedCount.getOrDefault(victim, 0);
			if(++count > getDamageLimitPerTick()) {
				event.setCancelled(true);
			}
			EPT_Jager.damagedCount.put(victim, count);
		}
		else {
			effectPlayers.remove(victim);
		}
	}
}
