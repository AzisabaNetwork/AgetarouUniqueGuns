package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import com.shampaggon.crackshot.events.WeaponShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.azisaba.lgw.core.LeonGunWar;
import net.azisaba.lgw.core.util.BattleTeam;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * EPT_Jager
 * 効果時間中、1Tick内に受けるダメージの回数を固定する
 * @author AburaAgeTarou
 */
public class EPT_Jager implements Listener {

	public final static String WEAPON_NAME = "EPT_Jager";

	public final static String KEY_DAMAGE_LIMIT_PER_TICK = "Damage_Limit_Per_Tick";
	public final static String KEY_EFFECT_DURATION = "Effect_Duration";
	public final static String KEY_DAMAGE_AMOUNT = "Damage_Amount";
	public final static String KEY_DAMAGE_RANGE = "Damage_Range";
	public final static String KEY_DAMAGE_MESSAGE = "Damage_Message";
	public final static String KEY_VICTIM_MESSAGE = "Victim_Message";
	public final static String KEY_VICTIM_LIMIT = "Victim_Limit";

	// 初期設定
	private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{
		set(KEY_DAMAGE_LIMIT_PER_TICK, 3);
		set(KEY_EFFECT_DURATION, 240);
		set(KEY_DAMAGE_AMOUNT, 1.0d);
		set(KEY_DAMAGE_RANGE, 12.0d);
		set(KEY_DAMAGE_MESSAGE, "&e[自身から%range%mに%player%を発見]");
		set(KEY_VICTIM_MESSAGE, "&e[%player%からの%weapon%&eによる電撃を受けた]");
		set(KEY_VICTIM_LIMIT, 3);
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
	 * 設定からダメージ量を取得
	 * @return double ダメージ量
	 */
	public static double getKeyDamageAmount() {
		try {
			return Double.parseDouble(getConfig(KEY_DAMAGE_AMOUNT).toString());
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_DAMAGE_AMOUNT);
			return defaultConfig.getDouble(KEY_DAMAGE_AMOUNT);
		}
	}

	/**
	 * 設定から対象範囲を取得
	 * @return double ダメージ量
	 */
	public static double getDamageRange() {
		try {
			return Double.parseDouble(getConfig(KEY_DAMAGE_RANGE).toString());
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_DAMAGE_RANGE);
			return defaultConfig.getDouble(KEY_DAMAGE_RANGE);
		}
	}

	/**
	 * 設定から与ダメージ時メッセージを取得
	 * @return String メッセージ(%range%: 範囲, %player%: プレイヤー名)
	 */
	public static String getDamageMessage() {
		try {
			return getConfig(KEY_DAMAGE_MESSAGE).toString();
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_DAMAGE_MESSAGE);
			return defaultConfig.getString(KEY_DAMAGE_MESSAGE);
		}
	}

	/**
	 * 設定から被ダメージ時メッセージを取得
	 * @return String メッセージ(%weapon%: 武器名, %player%: プレイヤー名)
	 */
	public static String getVictimMessage() {
		try {
			return getConfig(KEY_VICTIM_MESSAGE).toString();
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_VICTIM_MESSAGE);
			return defaultConfig.getString(KEY_VICTIM_MESSAGE);
		}
	}

	/**
	 * 設定から最大対象人数を取得
	 * @return double ダメージ量
	 */
	public static int getVictimLimit() {
		try {
			return Integer.parseInt(getConfig(KEY_VICTIM_LIMIT).toString());
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_VICTIM_LIMIT);
			return defaultConfig.getInt(KEY_VICTIM_LIMIT);
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
	 * メッセージ付きでダメージを与える
	 * @param player プレイヤー
	 * @param entity ダメージ対象
	 */
	public static void applyDamage(Player player, LivingEntity entity, ItemStack weapon) {

		// 武器名を取得
		String weaponTitle = API.getCSUtility().getWeaponTitle(player.getInventory().getItemInMainHand());
		if(weaponTitle == null) weaponTitle = "";
		String weaponName = API.getCSDirector().getString(weaponTitle + ".Item_Information.Item_Name");
		if(weaponName == null) weaponName = "";
		String orgWeaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
		if(orgWeaponTitle != null && !orgWeaponTitle.equals(weaponTitle)) {
			String orgWeaponName = API.getCSDirector().getString(orgWeaponTitle + ".Item_Information.Item_Name");
			weaponName += orgWeaponName != null ? " (" + orgWeaponName + ")" : "";
		}

		// 与ダメージメッセージ
		String damageMsg = getDamageMessage();
		BigDecimal distance = BigDecimal.valueOf(player.getLocation().distance(entity.getLocation()));
		distance = distance.setScale(1, RoundingMode.HALF_UP);
		damageMsg = damageMsg.replace("%range%", distance.toPlainString());
		damageMsg = damageMsg.replace("%player%", entity.getName());
		player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(damageMsg));

		// 被ダメージ
		double damageAmount = getKeyDamageAmount();
		String victimMsg = getVictimMessage();
		victimMsg = victimMsg.replace("%weapon%", weaponName);
		victimMsg = victimMsg.replace("%player%", player.getName());
		entity.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(victimMsg));
		entity.damage(damageAmount, player);
	}

	/**
	 * 範囲ダメージ
	 * @param player プレイヤー
	 */
	public static void applyDamage(Player player) {

		// 設定から性能を取得
		double damageRange = getDamageRange();
		int victimLimit = getVictimLimit();

		// チームを取得
		BattleTeam team = LeonGunWar.getPlugin().getManager().getBattleTeam(player);

		// 範囲内のより近い対象にダメージを与える
		player.getLocation().getNearbyLivingEntities(damageRange, damageRange, damageRange).stream()
				.filter(entity -> {
					if(entity.equals(player)) {
						return false;
					}
					if(entity instanceof Player) {
						Player victim = (Player) entity;
						return team != null && !team.equals(LeonGunWar.getPlugin().getManager().getBattleTeam(victim));
					}
					else {
						return !entity.isInvulnerable();
					}
				})
				.sorted(Comparator.comparingDouble(a -> a.getLocation().distance(player.getLocation())))
				.limit(victimLimit)
				.forEach(entity -> applyDamage(player, entity, player.getInventory().getItemInMainHand()));
	}

	/**
	 * 武器使用
	 * @param event 武器使用イベント
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWeaponShoot(WeaponShootEvent event) {
		String weaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
		if(!WEAPON_NAME.equals(weaponTitle)) return;

		applyDamage(event.getPlayer());
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
