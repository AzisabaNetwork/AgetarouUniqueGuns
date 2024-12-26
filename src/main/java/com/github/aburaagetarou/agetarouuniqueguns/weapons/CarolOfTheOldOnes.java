package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.github.aburaagetarou.agetarouuniqueguns.listeners.CSListeners;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.github.aburaagetarou.agetarouuniqueguns.utils.Utilities;
import com.shampaggon.crackshot.events.WeaponPreShootEvent;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Carol of the Old Ones
 * 残弾数を正気度のように扱い、0になったら即死する
 * @author AburaAgeTarou
 */
public class CarolOfTheOldOnes implements Listener {

	public final static String WEAPON_NAME = "CarolOfTheOldOnes";

	/**
	 * 弾丸発射後
	 * @param event 弾丸発射イベント
	 */
	@EventHandler
	public void onWeaponPreShoot(WeaponPreShootEvent event) {
		if (WEAPON_NAME.equals(CSUtilities.getOriginalWeaponName(event.getWeaponTitle()))) {
			if(API.getCSDirector().getAmmoBetweenBrackets(event.getPlayer(), WEAPON_NAME, event.getPlayer().getInventory().getItemInMainHand()) < 1) {
				Utilities.sendColoredMessage(event.getPlayer(), "&cあなたは忠告を守れなかった。");
				event.getPlayer().setHealth(0.0d);
			}
		}
	}
}
