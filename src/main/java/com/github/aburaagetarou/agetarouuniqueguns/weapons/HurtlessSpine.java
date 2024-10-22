package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.listeners.CSListeners;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.github.aburaagetarou.agetarouuniqueguns.utils.Utilities;
import com.shampaggon.crackshot.events.WeaponPreShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import me.DeeCaaD.CrackShotPlus.Skin;
import net.azisaba.lgw.core.events.PlayerKillEvent;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HurtlessSpine
 * キルごとに自身の悪性効果を解除する
 * 5キルごとに効果をリセットする
 * @author AburaAgeTarou
 */
public class HurtlessSpine extends WeaponBase {
	private final static UUID SPEED_MODIFIER_UUID = UUID.nameUUIDFromBytes("HurtlessSpine_Speed".getBytes());

	public final static String WEAPON_NAME = "HurtlessSpine";

	public final static String KEY_MOVEMENT_SPEED_MODIFIER = "Movement_Speed_Modifier";
	public final static String KEY_BULLET_SPREAD_MODIFIER = "Bullet_Spread_Modifier";

	// 初期設定
	private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{
		set(KEY_MOVEMENT_SPEED_MODIFIER, -0.05f);
		set(KEY_BULLET_SPREAD_MODIFIER, -0.2d);
	}}};

	// キルカウント
	static Map<Player, Integer> killCounts = new HashMap<>();

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
	 * 設定から移動速度上昇量を取得
	 * @return float 移動速度上昇量
	 */
	public static float getMovementSpeed() {
		try {
			return Float.parseFloat(getConfig(KEY_MOVEMENT_SPEED_MODIFIER).toString());
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_MOVEMENT_SPEED_MODIFIER);
			return (float) defaultConfig.getDouble(KEY_MOVEMENT_SPEED_MODIFIER);
		}
	}

	/**
	 * 設定からダメージ上昇量を取得
	 * @return double ダメージ上昇量
	 */
	public static double getBulletSpreadModifier() {
		try {
			return (double) getConfig(KEY_BULLET_SPREAD_MODIFIER);
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_BULLET_SPREAD_MODIFIER);
			return defaultConfig.getDouble(KEY_BULLET_SPREAD_MODIFIER);
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
	 * 移動速度を更新する
	 * @param player プレイヤー
	 */
	private static boolean updateWeaponData(ItemStack item, Player player) {
		if(item == null) return false;
		String weaponTitle = API.getCSUtility().getWeaponTitle(item);
		if(weaponTitle == null) return false;
		String orgWeaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
		if(!WEAPON_NAME.equals(orgWeaponTitle)) return false;

		// 現在の設定を取得
		ItemMeta meta = item.getItemMeta();
		if(killCounts.getOrDefault(player, 0) < 2){
			boolean needModifier = true;
			if(meta.getAttributeModifiers() != null) {
				for(AttributeModifier modifier : meta.getAttributeModifiers().get(Attribute.GENERIC_MOVEMENT_SPEED)) {
					if(modifier.getUniqueId().equals(SPEED_MODIFIER_UUID)) {
						if(Double.compare(modifier.getAmount(), getMovementSpeed()) == 0) {
							needModifier = false;
							break;
						}
						meta.removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
					}
				}
			}
			if(needModifier) {
				AttributeModifier modifier = new AttributeModifier(SPEED_MODIFIER_UUID, "generic.movement_speed", getMovementSpeed(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
				meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
			}
		}
		else {
			if(meta.getAttributeModifiers() != null) {
				for(AttributeModifier modifier : meta.getAttributeModifiers().get(Attribute.GENERIC_MOVEMENT_SPEED)) {
					if(modifier.getUniqueId().equals(SPEED_MODIFIER_UUID)) {
						meta.removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
					}
				}
			}
		}
		item.setItemMeta(meta);

		// スキンを設定
		int stage = killCounts.getOrDefault(player, 0);
		if(stage > 0 && stage <= 2) {
			return CSUtilities.applySkin(player, item, "Stage" + stage, Skin.SkinType.NORMAL) != null;
		}
		if(stage == 0) {
			return CSUtilities.applySkin(player, item, "Default_Skin", Skin.SkinType.NORMAL) != null;
		}
		return true;
	}

	/**
	 * キルカウントをインクリメントする
	 * @param player プレイヤー
	 */
	private static void incrementKillCount(Player player) {
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
				// 1キル時の効果：拡散率減少(射撃前処理で適用)
				case 1:
					Utilities.playSound(player, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 1.4f, 0L, 0L, 0L);
					break;

				// 2キル時の効果：移動速度上昇
				case 2:
				{
					Utilities.playSound(player, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 1.7f, 0L, 0L, 0L);
					break;
				}
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
	 * 武器射撃時処理
	 * @param event 射撃前イベント
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onWeaponPreShoot(WeaponPreShootEvent event) {
		if(!CSListeners.getDamagedWeaponTitle(event.getPlayer()).equals(WEAPON_NAME)) return;

		Player player = event.getPlayer();

		// 拡散率を変更
		double bulletSpread = event.getBulletSpread();
		if(killCounts.getOrDefault(player, 0) < 1) event.setBulletSpread(bulletSpread - getBulletSpreadModifier());
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
		incrementKillCount(event.getEntity().getKiller());
	}

	/**
	 * 死亡時処理
	 * @param event 死亡イベント
	 */
	@Override
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {

		// キルカウントをリセット
		resetKillCount(event.getEntity());
		super.onPlayerDeath(event);
	}
}
