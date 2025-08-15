package com.github.aburaagetarou.agetarouuniqueguns.listeners;

import com.github.aburaagetarou.agetarouuniqueguns.AgetarouUniqueGuns;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.events.*;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CrackShot Event Listener
 * @author AburaAgeTarou
 */
public class CSListeners implements Listener {

	// ダメージを与えた武器名
	static Map<Player, String> damagedWeaponTitle = new HashMap<>();
	static Map<Player, String> strictDamagedWeaponTitle = new HashMap<>();

	/**
	 * 最終ダメージを与えた武器名を取得
	 * @param player プレイヤー
	 * @return 武器名
	 */
	@Nonnull
	public static String getDamagedWeaponTitle(Player player) {
		return damagedWeaponTitle.getOrDefault(player, "");
	}

	/**
	 * 最終ダメージを与えた武器 or アタッチメント名を取得
	 * @param player プレイヤー
	 * @return 武器名
	 */
	@Nonnull
	public static String getStrictDamagedWeaponTitle(Player player) {
		return strictDamagedWeaponTitle.getOrDefault(player, "");
	}

	/**
	 * 与ダメージ時にダメージソースを削除
	 * @param event イベント
	 */
	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		if(event.getDamager() instanceof Player) {
			Player attacker = (Player) event.getDamager();
			damagedWeaponTitle.remove(attacker);
			strictDamagedWeaponTitle.remove(attacker);
		}
	}

	/**
	 * CS近接用クリック時処理
	 * @param event イベント
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
			if(!csAttack.containsKey(event.getPlayer())) return;

			// 最新ダメージソースを設定
			setDamagedWeaponTitle(event.getPlayer(), csAttack.get(event.getPlayer()));
			csAttack.remove(event.getPlayer());
		}
	}

	private final static Map<Player, String> csAttack = new HashMap<>();
	/**
	 * 銃弾ダメージ
	 * @param event イベント
	 */
	@EventHandler
	public void onWeaponDamageEntity(WeaponDamageEntityEvent event) {
		if(!(event.getDamager() instanceof Projectile)) {
			csAttack.put(event.getPlayer(), event.getWeaponTitle());
			Bukkit.getScheduler().runTaskLater(AgetarouUniqueGuns.getInstance(), () -> csAttack.remove(event.getPlayer()), 1L);
			return;
		}

		// 最新ダメージソースを設定
		setDamagedWeaponTitle(event.getPlayer(), event.getWeaponTitle());
	}

	/**
	 * 最新のCS武器ダメージソースを設定
	 * @param attacker 攻撃者
	 * @param weaponTitle 武器名
	 */
	public void setDamagedWeaponTitle(Player attacker, String weaponTitle) {
		damagedWeaponTitle.remove(attacker);
		strictDamagedWeaponTitle.remove(attacker);

		// ホットバーから武器を取得
		for(int i = 0; i < 9; i++) {
			String title = API.getCSUtility().getWeaponTitle(attacker.getInventory().getItem(i));
			if(title == null) continue;
			String meleeTitle = API.getCSDirector().getString(title + ".Item_Information.Melee_Attachment");
			if(weaponTitle.equals(title) || weaponTitle.equals(meleeTitle)) {

				// 武器名を保存
				title = CSUtilities.getOriginalWeaponName(title);
				damagedWeaponTitle.put(attacker, title);
				if(weaponTitle.equals(meleeTitle)) {
					strictDamagedWeaponTitle.put(attacker, meleeTitle);
				}
				else {
					strictDamagedWeaponTitle.put(attacker, title);
				}
				break;
			}
		}
	}
}
