package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.listeners.CSListeners;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.github.aburaagetarou.agetarouuniqueguns.utils.Utilities;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import com.shampaggon.crackshot.events.WeaponPreShootEvent;
import com.shampaggon.crackshot.events.WeaponShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.azisaba.lgw.core.LeonGunWar;
import net.azisaba.lgw.core.events.PlayerKillEvent;
import net.azisaba.lgw.core.util.BattleTeam;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 天使の断頭台
 * キルごとに武器を強化する
 * @author AburaAgeTarou
 */
public class TensinoDantouDai extends WeaponBase {
	private final static UUID SPEED_MODIFIER_UUID = UUID.nameUUIDFromBytes("TensinoDantouDai_Speed".getBytes());

	public final static String WEAPON_NAME = "TensinoDantouDai";
	public final static String WEAPON_MELEE_NAME = "TensinoDantouDai_knife";

	public final static String KEY_KNIFE_DAMAGE_MODIFIER = "Knife_Damage_Modifier";
	public final static String KEY_KNIFE_MOVEMENT_SPEED_MODIFIER = "Knife_Movement_Speed_Modifier";
	public final static String KEY_SKILL_REGENERATION_AMPLIFIER = "Skill_Regeneration_Amplifier";
	public final static String KEY_SKILL_REGENERATION_DURATION = "Skill_Regeneration_Duration";
	public final static String KEY_DAMAGE_REDUCTION_AMOUNT = "Damage_Reduction_Amount";
	public final static String KEY_DAMAGE_REDUCTION_RANGE = "Damage_Reduction_Range";

	// 初期設定
	private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{
		set(KEY_KNIFE_DAMAGE_MODIFIER, 0.75d);
		set(KEY_KNIFE_MOVEMENT_SPEED_MODIFIER, 0.05f);
		set(KEY_SKILL_REGENERATION_AMPLIFIER, 1);
		set(KEY_SKILL_REGENERATION_DURATION, 20);
		set(KEY_DAMAGE_REDUCTION_AMOUNT, 3.0d);
		set(KEY_DAMAGE_REDUCTION_RANGE, 15.0d);
	}}};

	// キルカウント
	static Map<Player, Integer> killCounts = new HashMap<>();

	/**
	 * 設定情報の取得
	 * @param key キー
	 * @return Object 設定情報、存在しない場合はデフォルト設定
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getConfig(String key) {
		ConfigurationSection config = WeaponConfig.getWeaponConfig(WEAPON_NAME);
		if(config == null) {
			config = defaultConfig;
		}
		if(!config.contains(key)) {
			config = defaultConfig;
		}
		try {
			return (T) config.get(key);
		}
		catch (ClassCastException ignore) {
			return (T) defaultConfig.get(key);
		}
	}

	/**
	 * 武器のメタデータを更新する
	 * @param item 武器アイテム
	 * @param player プレイヤー
	 */
	private static boolean updateWeaponData(ItemStack item, Player player) {
		if(item == null) return false;
		String weaponTitle = API.getCSUtility().getWeaponTitle(item);
		if(weaponTitle == null) return false;
		String orgWeaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
		if(!WEAPON_NAME.equals(orgWeaponTitle)) return false;

		// アイテムの移動速度を設定
		ItemMeta meta = item.getItemMeta();
		if(meta.getAttributeModifiers() != null) {
			for(AttributeModifier modifier : meta.getAttributeModifiers().get(Attribute.GENERIC_MOVEMENT_SPEED)) {
				if(modifier.getUniqueId().equals(SPEED_MODIFIER_UUID)) {
					meta.removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
				}
			}
			item.setItemMeta(meta);
		}
		if(killCounts.getOrDefault(player, 0) >= 2) {
			AttributeModifier modifier = new AttributeModifier(SPEED_MODIFIER_UUID, "generic.movement_speed", getConfig(KEY_KNIFE_MOVEMENT_SPEED_MODIFIER), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
			meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
			item.setItemMeta(meta);
		}
		return true;
	}

	/**
	 * キルカウントを設定する
	 * @param player プレイヤー
	 * @param count キルカウント
	 */
	public static void setKillCount(Player player, int count) {
		killCounts.put(player, count);
		for(int i = 0; i < 9; i++) {
			ItemStack item = player.getInventory().getItem(i);
			if(updateWeaponData(item, player)) {
				player.getInventory().setItem(i, item);
				break;
			}
		}
	}

	/**
	 * キルカウントのリセット
	 * @param player プレイヤー
	 */
	private static void resetKillCount(Player player) {
		killCounts.put(player, 0);
		for(int i = 0; i < 9; i++) {
			ItemStack item = player.getInventory().getItem(i);
			if(updateWeaponData(item, player)) {
				player.getInventory().setItem(i, item);
				break;
			}
		}
	}

	/**
	 * ダメージカット効果の付与
	 * @param player 起点プレイヤー
	 */
	private static void applyDamageReduction(Player player) {

		// 所属チームを取得
		BattleTeam team = LeonGunWar.getPlugin().getManager().getBattleTeam(player);

		// ダメージカット効果の付与
		double range = getConfig(KEY_DAMAGE_REDUCTION_RANGE);
		player.getLocation().getNearbyLivingEntities(range, range, range).stream()
				.filter(entity -> {
					if(entity.equals(player)) {
						return true;
					}
					if(entity instanceof Player) {
						Player victim = (Player) entity;
						return team != null && !team.equals(LeonGunWar.getPlugin().getManager().getBattleTeam(victim));
					}
					return false;
				})
				.forEach(entity -> applyDamageReduction(player, entity));
	}

	private static void applyDamageReduction(Player user, LivingEntity entity) {
		// 緩衝体力によってダメージカットを行う
		entity.setAbsorptionAmount(getConfig(KEY_DAMAGE_REDUCTION_AMOUNT));

		// メッセージ
		if(entity instanceof Player) {
			Utilities.sendColoredMessage((Player) entity, "&d" + user.getName() + "&6からダメージカット効果を得た");
		}
	}


	/**
	 * キルカウント増加
	 * @param player プレイヤー
	 */
	public static void incrementKillCount(Player player) {
		if(!CSListeners.getDamagedWeaponTitle(player).equals(WEAPON_NAME)) return;

		// キルカウントを取得
		int killCount = killCounts.getOrDefault(player, 0);

		// 5キルストリーク毎にリセット
		if(++killCount >= 5) {
			resetKillCount(player);
		}
		// キルカウントを増加
		else {
			// キル数ごとの効果付与
			switch (killCount) {
				// 4キル時の効果：周囲にいる味方のダメージカット
				case 4:
					applyDamageReduction(player);
					break;

				default:
					break;
			}
			setKillCount(player, killCount);
		}

	}

	/**
	 * プレイヤーのステータスを更新する
	 * @param player プレイヤー
	 */
	@Override
	public void updateStats(Player player) {
		for(int i = 0; i < 9; i++) {
			ItemStack item = player.getInventory().getItem(i);
			if(updateWeaponData(item, player)) {
				player.getInventory().setItem(i, item);
				break;
			}
		}
	}

	/**
	 * ダメージ時処理
	 * @param event ダメージイベント
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onWeaponDamageEntity(WeaponDamageEntityEvent event) {
		Player player = event.getPlayer();
		if(!WEAPON_MELEE_NAME.equals(CSListeners.getStrictDamagedWeaponTitle(player))) return;

		// キルカウントを取得
		int killCount = killCounts.getOrDefault(player, 0);

		// 1キル時の効果：ダメージ上昇
		if(killCount >= 1) {
			double modifier = getConfig(KEY_KNIFE_DAMAGE_MODIFIER);
			event.setDamage(event.getDamage() + modifier);
		}
	}

	/**
	 * スキル使用時処理
	 * @param event ダメージイベント
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onWeaponShootEvent(WeaponPreShootEvent event) {
		if(!WEAPON_NAME.equals(CSUtilities.getOriginalWeaponName(event.getWeaponTitle()))) return;

		Player player = event.getPlayer();

		// キルカウントを取得
		int killCount = killCounts.getOrDefault(player, 0);

		// 3キル時の効果：再生付与
		if(killCount >= 3) {
			int duration = getConfig(KEY_SKILL_REGENERATION_DURATION);
			int amplifier = getConfig(KEY_SKILL_REGENERATION_AMPLIFIER);
			Bukkit.getScheduler().runTaskLater(AgetarouUniqueGuns.getInstance(), () ->
				player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, amplifier))
			, 1L);
		}
	}

	/**
	 * キル時処理
	 * @param event キルイベント
	 */
	@EventHandler
	public void onPlayerKill(PlayerKillEvent event) {
		incrementKillCount(event.getPlayer());
	}

	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		if(event.getEntity() instanceof Player) return;
		incrementKillCount(event.getEntity().getKiller());
	}
}
