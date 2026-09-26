package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.WeaponConfig;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.github.aburaagetarou.agetarouuniqueguns.utils.EnhancedTask;
import com.github.aburaagetarou.agetarouuniqueguns.utils.Utilities;
import com.shampaggon.crackshot.events.WeaponPreShootEvent;
import com.shampaggon.crackshot.events.WeaponReloadCompleteEvent;
import com.shampaggon.crackshot.events.WeaponReloadEvent;
import com.shampaggon.crackshot.events.WeaponShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TimeCompressor
 * らんちゎー
 * @author AburaAgeTarou
 */
public class TimeCompressor implements Listener {
	public final static String WEAPON_NAME = "TimeCompressor";

	public final static String KEY_COMPRESSED_WEAPON_NAME = "Compressed_Weapon_Name";
	public final static String KEY_COMPRESS_TIME = "Compress_Time";
	public final static String KEY_COMPRESSING_SOUND = "Compressing_Sound";

	// 初期設定
	private static final ConfigurationSection defaultConfig = new YamlConfiguration(){{{

		// 最大チャージ
		set(KEY_COMPRESSED_WEAPON_NAME, "TimeCompressor_2");

		// 圧縮時間（tick）
		set(KEY_COMPRESS_TIME, 40);
	}}};

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
	 * 持ち替え時リセット
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
		if(WEAPON_NAME.equals(orgWeaponTitle)) {
			return;
		}

		// 圧縮後の武器が残っている場合は元の武器に戻す
		String compWeaponTitle = getConfig(KEY_COMPRESSED_WEAPON_NAME);
		if(compWeaponTitle == null || compWeaponTitle.isEmpty()) {
			return;
		}
		if(compWeaponTitle.equals(orgWeaponTitle)) {
			item = CSUtilities.getWeapon(WEAPON_NAME);
			int ammo = API.getCSDirector().getAmmoBetweenBrackets(event.getPlayer(), compWeaponTitle, item);
			if(ammo > 0) {
				ammo = API.getCSDirector().getInt(WEAPON_NAME + ".Reload.Reload_Amount");
			}
			API.getCSDirector().csminion.replaceBrackets(item, ""+ammo, WEAPON_NAME);
			player.getInventory().setItem(slot, item);
		}
	}

	/**
	 * 一定時間スニークした場合に圧縮後の武器に変化させる
	 * @param event イベント
	 */
	@EventHandler
	public void onPlayerToggleSneakEvent(PlayerToggleSneakEvent event) {
		Player player = event.getPlayer();
		ItemStack item = player.getInventory().getItem(player.getInventory().getHeldItemSlot());
		String weaponTitle = API.getCSUtility().getWeaponTitle(item);
		String orgWeaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);
		if(!WEAPON_NAME.equals(orgWeaponTitle)) {
			return;
		}
		String compWeaponTitle = getConfig(KEY_COMPRESSED_WEAPON_NAME);
		if (compWeaponTitle == null || compWeaponTitle.isEmpty()) {
			return;
		}

		// 弾数がMAXの場合のみ変化
		int ammo = API.getCSDirector().getAmmoBetweenBrackets(event.getPlayer(), WEAPON_NAME, item);
		int maxAmmo = API.getCSDirector().getInt(WEAPON_NAME + ".Reload.Reload_Amount");
		if(ammo != maxAmmo) {
			return;
		}

		// 一定秒数スニークを継続した場合、圧縮後の武器に変化させる
		List<WeaponConfig.SoundSetting> sounds = WeaponConfig.getSoundSetting(WEAPON_NAME, KEY_COMPRESSING_SOUND);
		EnhancedTask soundTask = Utilities.playSounds(sounds, player);
		int compressTime = getConfig(KEY_COMPRESS_TIME);
		int startTime = Bukkit.getCurrentTick();
		Bukkit.getScheduler().runTaskTimer(AgetarouUniqueGuns.getInstance(), (task) -> {
			// 持ち替えた場合は判定終了
			ItemStack item2 = player.getInventory().getItem(player.getInventory().getHeldItemSlot());
			String weaponTitle2 = API.getCSUtility().getWeaponTitle(item2);
			String orgWeaponTitle2 = CSUtilities.getOriginalWeaponName(weaponTitle2);
			if(!(WEAPON_NAME.equals(orgWeaponTitle2) || compWeaponTitle.equals(orgWeaponTitle2))) {
				if(soundTask != null) soundTask.cancel();
				Bukkit.getScheduler().cancelTask(task.getTaskId());
				return;
			}

			int ammo2 = API.getCSDirector().getAmmoBetweenBrackets(event.getPlayer(), orgWeaponTitle2, item2);
			int maxAmmo2 = API.getCSDirector().getInt(orgWeaponTitle2 + ".Reload.Reload_Amount");
			if(ammo2 != maxAmmo2) {
				if(soundTask != null) soundTask.cancel();
				Bukkit.getScheduler().cancelTask(task.getTaskId());
				return;
			}

			if (Bukkit.getCurrentTick() - startTime >= compressTime && WEAPON_NAME.equals(orgWeaponTitle2) ) {
				if(soundTask != null) soundTask.cancel();
				ItemStack replItem = CSUtilities.getWeapon(compWeaponTitle);
				API.getCSDirector().csminion.replaceBrackets(replItem, "1", compWeaponTitle);
				player.getInventory().setItem(player.getInventory().getHeldItemSlot(), replItem);
				return;
			}

			// スニークを解除した場合は圧縮前に戻す
			if (!player.isSneaking()) {
				ItemStack replItem = CSUtilities.getWeapon(weaponTitle);
				API.getCSDirector().csminion.replaceBrackets(replItem, ""+maxAmmo, weaponTitle);
				player.getInventory().setItem(player.getInventory().getHeldItemSlot(), replItem);
				if(soundTask != null) soundTask.cancel();
				Bukkit.getScheduler().cancelTask(task.getTaskId());
			}
		}, 1L, 1L);
	}


	/**
	 * 銃発射処理の割り込み
	 * @param event 銃発射イベント
	 */
	@EventHandler
	public void onWeaponShootEvent(WeaponShootEvent event) {

		String weaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
		String compWeaponTitle = getConfig(KEY_COMPRESSED_WEAPON_NAME);
		if (compWeaponTitle == null || compWeaponTitle.isEmpty()) {
			return;
		}
		if(compWeaponTitle.equals(weaponTitle)) {
			// 圧縮後の武器を発射した場合は圧縮前の武器に戻す
			Player player = event.getPlayer();
			ItemStack replItem = CSUtilities.getWeapon(WEAPON_NAME);
			API.getCSDirector().csminion.replaceBrackets(replItem, "0", WEAPON_NAME);
			player.getInventory().setItem(player.getInventory().getHeldItemSlot(), replItem);
		}
	}
}
