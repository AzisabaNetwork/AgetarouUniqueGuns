package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.listeners.CSListeners;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.github.aburaagetarou.agetarouuniqueguns.utils.Utilities;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.azisaba.lgw.core.events.PlayerKillEvent;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HurtfulSpine
 * キルごとに自身に良性効果を付与する
 * 5キルごとに効果をリセットする
 * @author AburaAgeTarou
 */
public class HurtfulSpine extends WeaponBase {
	private final static UUID SPEED_MODIFIER_UUID = UUID.nameUUIDFromBytes("HurtlessSpine_Speed".getBytes());

	public final static String WEAPON_NAME = "HurtfulSpine";

	public final static String KEY_MOVEMENT_SPEED_MODIFIER = "Movement_Speed_Modifier";
	public final static String KEY_HEAL = "Heal";
	public final static String KEY_DAMAGE_MODIFIER = "Damage_Modifier";

	// 初期設定
	private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{
		set(KEY_MOVEMENT_SPEED_MODIFIER, 0.05f);
		set(KEY_HEAL, 5.0d);
		set(KEY_DAMAGE_MODIFIER, 0.75d);
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
	 * 設定から回復量を取得
	 * @return double 回復量
	 */
	public static double getHeal() {
		try {
			return (double) getConfig(KEY_HEAL);
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_HEAL);
			return defaultConfig.getDouble(KEY_HEAL);
		}
	}

	/**
	 * 設定からダメージ上昇量を取得
	 * @return double ダメージ上昇量
	 */
	public static double getDamageModifier() {
		try {
			return (double) getConfig(KEY_DAMAGE_MODIFIER);
		}
		catch (ClassCastException e) {
			AgetarouUniqueGuns.getInstance().getLogger().warning("Invalid configuration value: " + WEAPON_NAME + "." + KEY_DAMAGE_MODIFIER);
			return defaultConfig.getDouble(KEY_DAMAGE_MODIFIER);
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
			if(setItemWalkSpeed(item, player)) {
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
			if(setItemWalkSpeed(item, player)) {
				player.getInventory().setItem(i, item);
				break;
			}
		}
	}

	/**
	 * 移動速度を更新する
	 * @param player プレイヤー
	 */
	private static boolean setItemWalkSpeed(ItemStack item, Player player) {
		if(item == null) return false;
		String weaponTitle = API.getCSUtility().getWeaponTitle(item);
		if(weaponTitle == null) return false;
		weaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
		if(!WEAPON_NAME.equals(weaponTitle)) return false;

		// アイテムの移動速度を設定
		ItemMeta meta = item.getItemMeta();
		if(killCounts.getOrDefault(player, 0) >= 3) {
			if(meta.getAttributeModifiers() != null) {
				for(AttributeModifier modifier : meta.getAttributeModifiers().get(Attribute.GENERIC_MOVEMENT_SPEED)) {
					if(modifier.getUniqueId().equals(SPEED_MODIFIER_UUID)) {
						if(Double.compare(modifier.getAmount(), getMovementSpeed()) == 0) return false;
						meta.removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
					}
				}
			}
			AttributeModifier modifier = new AttributeModifier(SPEED_MODIFIER_UUID, "generic.movement_speed", getMovementSpeed(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
			meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
		}
		else {
			if(meta.getAttributeModifiers() != null) {
				for(AttributeModifier modifier : meta.getAttributeModifiers().get(Attribute.GENERIC_MOVEMENT_SPEED)) {
					meta.removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
				}
			}
		}
		item.setItemMeta(meta);
		return true;
	}

	/**
	 * プレイヤーのステータスを設定
	 * @param player プレイヤー
	 */
	@Override
	public void updateStats(Player player) {
		for(int i = 0; i < 9; i++) {
			ItemStack item = player.getInventory().getItem(i);
			if(setItemWalkSpeed(item, player)) {
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
		if(!CSListeners.getDamagedWeaponTitle(event.getPlayer()).equals(WEAPON_NAME)) return;

		Player player = event.getPlayer();

		// キルカウントを取得
		int killCount = killCounts.getOrDefault(player, 0);

		// 1キル時の効果：ダメージ上昇
		if(killCount >= 1) {
			event.setDamage(event.getDamage() + getDamageModifier());
		}
	}

	/**
	 * キル時処理
	 * @param event キルイベント
	 */
	@EventHandler
	public void onPlayerKill(PlayerKillEvent event) {
		if(!CSListeners.getDamagedWeaponTitle(event.getPlayer()).equals(WEAPON_NAME)) return;

		Player player = event.getPlayer();

		// キルカウントを取得
		int killCount = killCounts.getOrDefault(player, 0);

		// 5キルストリーク毎にリセット
		if(++killCount >= 5) {
			resetKillCount(player);
		}
		// キルカウントを増加
		else {
			killCounts.put(player, killCount);

			// キル数ごとの効果付与
			switch (killCount) {
				// 1キル時の効果：ダメージ上昇(ダメージ時処理で適用)
				case 1:
					Utilities.playSound(player, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.6f, 0L, 18L, 3L);
					Utilities.playSound(player, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.8f, 4L, 18L, 3L);
					break;

				// 2キル時の効果：体力回復
				case 2:
				{
					// 体力最大値を取得
					double max = 20.0d;
					Optional<AttributeInstance> attr = Optional.ofNullable(player.getAttribute(Attribute.GENERIC_MAX_HEALTH));
					if(attr.isPresent()) max = attr.get().getValue();

					// 即時回復
					event.getPlayer().setHealth(Math.min(player.getHealth() + getHeal(), max));

					Utilities.playSound(player, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.0f, 0L, 15L, 3L);
					Utilities.playSound(player, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.2f, 4L, 15L, 3L);
					break;
				}

				// 3キル時の効果：移動速度上昇
				case 3:
				{
					// 移動速度を上昇
					updateStats(player);
					Utilities.playSound(player, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.4f, 0L, 12L, 3L);
					Utilities.playSound(player, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 1.6f, 4L, 12L, 3L);
					break;
				}
			}
		}
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
