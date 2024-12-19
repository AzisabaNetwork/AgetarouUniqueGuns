package com.github.aburaagetarou.agetarouuniqueguns.listeners;

import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.shampaggon.crackshot.events.WeaponDamageEntityEvent;
import me.DeeCaaD.CrackShotPlus.API;
import net.azisaba.lgw.core.LeonGunWar;
import net.azisaba.lgw.core.util.BattleTeam;
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
	 * CS武器ダメージ
	 * @param event イベント
	 */
	@EventHandler
	public void onWeaponDamageEntity(WeaponDamageEntityEvent event) {
		damagedWeaponTitle.remove(event.getPlayer());
		strictDamagedWeaponTitle.remove(event.getPlayer());

		// ホットバーから武器を取得
		for(int i = 0; i < 9; i++) {
			String title = API.getCSUtility().getWeaponTitle(event.getPlayer().getInventory().getItem(i));
			if(title == null) continue;
			String meleeTitle = API.getCSDirector().getString(title + ".Item_Information.Melee_Attachment");
			if(event.getWeaponTitle().equals(title) || event.getWeaponTitle().equals(meleeTitle)) {

				// 武器名を保存
				title = CSUtilities.getOriginalWeaponName(title);
				damagedWeaponTitle.put(event.getPlayer(), title);
				if(event.getWeaponTitle().equals(meleeTitle)) {
					strictDamagedWeaponTitle.put(event.getPlayer(), meleeTitle);
				}
				else {
					strictDamagedWeaponTitle.put(event.getPlayer(), title);
				}
				break;
			}
		}

		// 味方に対する攻撃を無効化する
		if(event.getVictim() instanceof Player) {
			Player victim = (Player) event.getVictim();
			if(LeonGunWar.getPlugin().getManager().isMatching()) {
				BattleTeam attackerTeam = LeonGunWar.getPlugin().getManager().getBattleTeam(event.getPlayer());
				BattleTeam damagedTeam = LeonGunWar.getPlugin().getManager().getBattleTeam(victim);
				if(attackerTeam.equals(damagedTeam)) {
					event.setCancelled(true);
				}
			}
		}
	}
}
