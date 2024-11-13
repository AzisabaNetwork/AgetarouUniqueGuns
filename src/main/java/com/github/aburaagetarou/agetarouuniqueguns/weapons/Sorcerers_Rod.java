package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.events.WeaponPreShootEvent;
import com.shampaggon.crackshot.events.WeaponReloadCompleteEvent;
import com.shampaggon.crackshot.events.WeaponReloadEvent;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sorcerers_Rod
 * 魔法弾を発射する
 * @author AburaAgeTarou
 */
public class Sorcerers_Rod implements Listener {
	private final static UUID SPEED_MODIFIER_UUID = UUID.nameUUIDFromBytes("Sorcerers_Rod_Speed".getBytes());

	public final static String WEAPON_NAME = "Sorcerers_Rod";

	public final static String KEY_MANA_REFILL_START_TIME = "Mana_Refill_Start_Time";
	public final static String KEY_MANA_REFILL_AMOUNT = "Mana_Refill_Amount";
	public final static String KEY_MOVEMENT_SPEED_MODIFIER = "Movement_Speed_Modifier";

	// 初期設定
	private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{

		// 無(LMG)
		set(Elemental.BLANK.getKey() + "." + KEY_MANA_REFILL_START_TIME, 30);
		set(Elemental.BLANK.getKey() + "." + KEY_MANA_REFILL_AMOUNT, 1);
		set(Elemental.BLANK.getKey() + "." + KEY_MOVEMENT_SPEED_MODIFIER, -0.02d);

		// 火(AR)
		set(Elemental.FIRE.getKey() + "." + KEY_MANA_REFILL_START_TIME, 20);
		set(Elemental.FIRE.getKey() + "." + KEY_MANA_REFILL_AMOUNT, 2);
		set(Elemental.FIRE.getKey() + "." + KEY_MOVEMENT_SPEED_MODIFIER, 0.0d);

		// 水(SAR)
		set(Elemental.WATER.getKey() + "." + KEY_MANA_REFILL_START_TIME, 10);
		set(Elemental.WATER.getKey() + "." + KEY_MANA_REFILL_AMOUNT, 1);
		set(Elemental.WATER.getKey() + "." + KEY_MOVEMENT_SPEED_MODIFIER, 0.0d);

		// 土(DMR)
		set(Elemental.EARTH.getKey() + "." + KEY_MANA_REFILL_START_TIME, 10);
		set(Elemental.EARTH.getKey() + "." + KEY_MANA_REFILL_AMOUNT, 1);
		set(Elemental.EARTH.getKey() + "." + KEY_MOVEMENT_SPEED_MODIFIER, -0.01d);

		// 風(SMG)
		set(Elemental.WIND.getKey() + "." + KEY_MANA_REFILL_START_TIME, 15);
		set(Elemental.WIND.getKey() + "." + KEY_MANA_REFILL_AMOUNT, 1);
		set(Elemental.WIND.getKey() + "." + KEY_MOVEMENT_SPEED_MODIFIER, 0.01d);
	}}};

	private static BukkitTask timerTask;

	/**
	 * マナ自動回復タスクの開始
	 */
	public static void initManaRefillTask() {
		timerTask = Bukkit.getScheduler().runTaskTimer(AgetarouUniqueGuns.getInstance(), Sorcerers_Rod::refillMana, 0, 1);
	}

	/**
	 * タスク停止
	 */
	public static void stopManaRefillTask() {
		if(timerTask != null) {
			timerTask.cancel();
		}
	}

	private final static Map<Entity, Integer> lastActionTime = new HashMap<>();
	private final static Map<Entity, Integer> healStartTime = new HashMap<>();

	/**
	 * マナ回復
	 */
	public static void refillMana() {
		for(Player player : Bukkit.getOnlinePlayers()) {
			ItemStack item = player.getInventory().getItemInMainHand();
			String weaponTitle = API.getCSUtility().getWeaponTitle(item);
			String orgWeaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
			if(!WEAPON_NAME.equals(orgWeaponTitle)) {
				continue;
			}
			Elemental elem = Grimoire.getElemental(player);
			int startTime = getConfig(elem, KEY_MANA_REFILL_START_TIME);
			int interval = API.getCSDirector().getInt(WEAPON_NAME + "_" + elem.getKey() + ".Reload.Reload_Duration");
			int amount = getConfig(elem, KEY_MANA_REFILL_AMOUNT);
			int maxMana = API.getCSDirector().getInt(WEAPON_NAME + "_" + elem.getKey() + ".Reload.Reload_Amount");
			int currentTick = Bukkit.getCurrentTick();
			int lastAction = lastActionTime.getOrDefault(player, 0);

			// 最終射撃から一定時間経過している場合
			if(currentTick - lastAction > startTime) {
				// 回復開始時間を記録
				int healStart = healStartTime.getOrDefault(player, 0);
				if(healStart == 0) {
					healStart = currentTick;
					healStartTime.put(player, healStart);
				}

				// 即時回復
				if(interval == 0) {
					API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(maxMana), WEAPON_NAME + "_" + elem.getKey());
					continue;
				}

				// 自動回復
				if((currentTick - healStart) % interval == 0) {
					int ammo = API.getCSDirector().getAmmoBetweenBrackets(player, WEAPON_NAME + "_" + elem.getKey(), item);
					if(ammo == maxMana) {
						continue;
					}
					ammo += amount;
					if(ammo > maxMana) {
						ammo = maxMana;
						healStartTime.remove(player);
					}
					API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(ammo), WEAPON_NAME + "_" + elem.getKey());
				}
				player.getInventory().setItemInMainHand(item);
			}
			else {
				healStartTime.remove(player);
			}
		}
	}

	/**
	 * 設定情報の取得
	 * @param elem 属性
	 * @param configKey キー
	 * @return Object 設定情報、存在しない場合はデフォルト設定
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getConfig(Elemental elem, String configKey) {
		ConfigurationSection config = WeaponConfig.getWeaponConfig(WEAPON_NAME);
		String key = elem.getKey() + "." + configKey;
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
	 * 銃発射処理の割り込み
	 * @param event 銃発射イベント
	 */
	@EventHandler
	public void onWeaponPreShoot(WeaponPreShootEvent event) {

		// 発射時間を記録
		String weaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
		if(weaponTitle != null && weaponTitle.startsWith(WEAPON_NAME)) {
			lastActionTime.put(event.getPlayer(), Bukkit.getCurrentTick());
		}
	}

	// 変更前属性
	private final static Map<Player, Elemental> lastElemental = new HashMap<>();

	/**
	 * 持ち替え時に属性ごとの弾数設定
	 * @param event イベント
	 */
	@EventHandler
	public void onPlayerItemHeld(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		int slot = event.getNewSlot();
		ItemStack item = player.getInventory().getItem(slot);
		if(item == null) return;
		String weaponTitle = API.getCSUtility().getWeaponTitle(item);
		String orgWeaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
		if(!WEAPON_NAME.equals(orgWeaponTitle)) {
			return;
		}

		Elemental elem = Grimoire.getElemental(player);
		if(!lastElemental.containsKey(player) || lastElemental.get(player) != elem) {
			API.getCSDirector().csminion.resetItemName(item, weaponTitle + "_" + elem.getKey());
			API.getCSDirector().csminion.replaceBrackets(item, "0", WEAPON_NAME + "_" + elem.getKey());
		}
		lastElemental.put(player, elem);

		// アイテムの移動速度を設定
		ItemMeta meta = item.getItemMeta();
		double speed = getConfig(elem, KEY_MOVEMENT_SPEED_MODIFIER);
		if(meta.getAttributeModifiers() != null) {
			for(AttributeModifier modifier : meta.getAttributeModifiers().get(Attribute.GENERIC_MOVEMENT_SPEED)) {
				if(modifier.getUniqueId().equals(SPEED_MODIFIER_UUID)) {
					meta.removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
				}
			}
		}
		AttributeModifier modifier = new AttributeModifier(SPEED_MODIFIER_UUID, "generic.movement_speed", speed, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
		meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, modifier);
		item.setItemMeta(meta);

		player.getInventory().setItem(slot, item);
	}

	private final static Map<Player, Integer> beforeReloadAmmo = new HashMap<>();

	/**
	 * 手動リロードを禁止
	 * @param event リロードイベント
	 */
	@EventHandler
	public void onWeaponPreReload(WeaponReloadEvent event) {

		// リロード前の弾数を保存
		String weaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
		if(weaponTitle != null && weaponTitle.startsWith(WEAPON_NAME)) {
			ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
			Elemental elem = Grimoire.getElemental(event.getPlayer());
			int ammo = API.getCSDirector().getAmmoBetweenBrackets(event.getPlayer(), WEAPON_NAME + "_" + elem.getKey(), item);
			beforeReloadAmmo.put(event.getPlayer(), ammo);
		}
	}

	@EventHandler
	public void onWeaponReloadComplete(WeaponReloadCompleteEvent event) {
		if(beforeReloadAmmo.containsKey(event.getPlayer())) {
			String weaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
			if(weaponTitle != null && weaponTitle.startsWith(WEAPON_NAME)) {
				ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
				Elemental elem = Grimoire.getElemental(event.getPlayer());
				API.getCSDirector().csminion.replaceBrackets(item, String.valueOf(beforeReloadAmmo.get(event.getPlayer())), WEAPON_NAME + "_" + elem.getKey());
				beforeReloadAmmo.remove(event.getPlayer());
			}
		}
	}

	/**
	 * 属性の定義
	 */
	public enum Elemental {
		BLANK("Blank", "無"),
		FIRE("Fire", "火"),
		WATER("Water", "水"),
		EARTH("Earth", "土"),
		WIND("Wind", "風"),;

		// 設定キー名
		private final String key;

		// 名称
		private final String name;

		/**
		 * コンストラクタ
		 * @param key 設定キー名
		 * @param name 名称
		 */
		Elemental(String key, String name) {
			this.key = key;
			this.name = name;
		}

		/**
		 * 設定キー名を取得
		 * @return String 設定キー名
		 */
		public String getKey() {
			return key;
		}

		/**
		 * 属性名を取得
		 * @return String 属性名
		 */
		public String getName() {
			return name;
		}

		/**
		 * 設定キー名から属性を取得
		 * @param key 設定キー名
		 * @return Elemental 属性
		 */
		public static Elemental fromKey(String key) {
			for(Elemental e : values()) {
				if(e.key.equals(key)) {
					return e;
				}
			}
			return BLANK;
		}
	}
}
