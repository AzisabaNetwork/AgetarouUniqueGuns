package com.github.aburaagetarou.agetarouuniqueguns;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.github.aburaagetarou.agetarouuniqueguns.weapons.HurtfulSpine;
import com.github.aburaagetarou.agetarouuniqueguns.weapons.HurtlessSpine;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@CommandAlias("agetarouuniqueguns|aug")
@Description("AgetarouUniqueGuns Command")
public class AUGCommand extends BaseCommand {

	@Dependency
	private AgetarouUniqueGuns plugin;

	@HelpCommand
	@CommandPermission("agetarouuniqueguns.help")
	public void onHelp(CommandSender sender, CommandHelp help) {
		help.showHelp();
	}

	@Subcommand("reload")
	@CommandPermission("agetarouuniqueguns.reload")
	@Description("設定情報を再読み込みします")
	public void onReload(CommandSender sender) {

		plugin.loadDefaultWeaponConfig();
		sender.sendMessage("設定情報を再読み込みしました");
	}

	@Subcommand("setvariable")
	@CommandPermission("agetarouuniqueguns.setvariable")
	@Description("所持中の武器の固有変数の値を変更します。")
	public void onSetVariable(Player player, String key, String value) {
		ItemStack held = player.getInventory().getItemInMainHand();
		String weaponTitle = API.getCSUtility().getWeaponTitle(held);
		weaponTitle = weaponTitle != null ? weaponTitle : "";
		weaponTitle = CSUtilities.getOriginalWeaponName(weaponTitle);

		switch(weaponTitle) {

			// HurtlessSpine
			case HurtlessSpine.WEAPON_NAME:
			{
				if(key.equalsIgnoreCase("killcount")) {
					try {
						int count = Integer.parseInt(value);
						HurtlessSpine.setKillCount(player, count);
					}
					catch (NumberFormatException e) {
						player.sendMessage("数値を入力してください");
					}
				}
				break;
			}

			// HurtfulSpine
			case HurtfulSpine.WEAPON_NAME:
			{
				if(key.equalsIgnoreCase("killcount")) {
					try {
						int count = Integer.parseInt(value);
						HurtfulSpine.setKillCount(player, count);
					}
					catch (NumberFormatException e) {
						player.sendMessage("数値を入力してください");
					}
				}
				break;
			}
		}
	}
}
