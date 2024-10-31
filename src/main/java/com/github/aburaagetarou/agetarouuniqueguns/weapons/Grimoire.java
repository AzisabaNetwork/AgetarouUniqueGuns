package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.events.WeaponPreShootEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

public abstract class Grimoire {

	// 武器名
	private static final String WEAPON_NAME = "Grimoire";

	// 属性設定
	private static final Map<Entity, Sorcerers_Rod.Elemental> elemental = new HashMap<>();

	/**
	 * イベント一括登録
	 */
	public static void registerEvents() {
		// 無属性
		Bukkit.getPluginManager().registerEvents(new GrimoireBlank(), AgetarouUniqueGuns.getInstance());
		// 火属性
		Bukkit.getPluginManager().registerEvents(new GrimoireFire(), AgetarouUniqueGuns.getInstance());
		// 水属性
		Bukkit.getPluginManager().registerEvents(new GrimoireWater(), AgetarouUniqueGuns.getInstance());
		// 土属性
		Bukkit.getPluginManager().registerEvents(new GrimoireEarth(), AgetarouUniqueGuns.getInstance());
		// 風属性
		Bukkit.getPluginManager().registerEvents(new GrimoireWind(), AgetarouUniqueGuns.getInstance());
	}

	/**
	 * 属性の取得
	 * @param entity エンティティ
	 * @return Elemental 属性
	 */
	public static Sorcerers_Rod.Elemental getElemental(Entity entity) {
		return elemental.getOrDefault(entity, Sorcerers_Rod.Elemental.BLANK);
	}

	/**
	 * 属性の設定
	 * @param entity 対象
	 * @param element 属性
	 */
	public static void setElemental(Entity entity, Sorcerers_Rod.Elemental element) {
		elemental.put(entity, element);
	}

	/**
	 * 属性の取得
	 * @return Elemental 属性
	 */
	public abstract Sorcerers_Rod.Elemental getElemental();

	/**
	 * 使用時共通処理
	 * @param event WeaponPreShootEvent
	 */
	@EventHandler
	public void onWeaponPreShoot(WeaponPreShootEvent event) {
		String usedWeaponTitle = CSUtilities.getOriginalWeaponName(event.getWeaponTitle());
		String weaponTitle = WEAPON_NAME + "_" + getElemental().getKey();
		if(!weaponTitle.equals(usedWeaponTitle)) return;

		// 属性設定
		setElemental(event.getPlayer(), getElemental());
	}

	/**
	 * 無属性の魔導書
	 */
	public static class GrimoireBlank extends Grimoire implements Listener {

		@Override
		public Sorcerers_Rod.Elemental getElemental() {
			return Sorcerers_Rod.Elemental.BLANK;
		}
	}

	/**
	 * 火属性の魔導書
	 */
	public static class GrimoireFire extends Grimoire implements Listener {

		@Override
		public Sorcerers_Rod.Elemental getElemental() {
			return Sorcerers_Rod.Elemental.FIRE;
		}
	}

	/**
	 * 水属性の魔導書
	 */
	public static class GrimoireWater extends Grimoire implements Listener {

		@Override
		public Sorcerers_Rod.Elemental getElemental() {
			return Sorcerers_Rod.Elemental.WATER;
		}
	}

	/**
	 * 土属性の魔導書
	 */
	public static class GrimoireEarth extends Grimoire implements Listener {

		@Override
		public Sorcerers_Rod.Elemental getElemental() {
			return Sorcerers_Rod.Elemental.EARTH;
		}
	}

	/**
	 * 土属性の魔導書
	 */
	public static class GrimoireWind extends Grimoire implements Listener {

		@Override
		public Sorcerers_Rod.Elemental getElemental() {
			return Sorcerers_Rod.Elemental.WIND;
		}
	}
}
