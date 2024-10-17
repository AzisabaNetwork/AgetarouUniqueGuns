package com.github.aburaagetarou.agetarouuniqueguns.listeners;

import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * CrackShot Event Listener
 * @author AburaAgeTarou
 */
public class CSListeners implements Listener {

	// ダメージを与えた武器名
	static Map<Player, String> damagedWeaponTitle = new HashMap<>();

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
	 * CS武器ダメージ
	 * @param event イベント
	 */
	@EventHandler
	public void onWeaponDamageEntity(WeaponDamageEntityEvent event) {
		damagedWeaponTitle.remove(event.getPlayer());

		// ホットバーから武器を取得
		for(int i = 0; i < 9; i++) {
			String title = API.getCSUtility().getWeaponTitle(event.getPlayer().getInventory().getItem(i));
			if(title == null) continue;
			title = CSUtilities.getOriginalWeaponName(title);
			if(event.getWeaponTitle().equals(title)) {

				// 武器名を保存
				damagedWeaponTitle.put(event.getPlayer(), title);
				break;
			}
		}
	}
}
