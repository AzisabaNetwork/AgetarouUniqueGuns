package com.github.aburaagetarou.agetarouuniqueguns;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import com.github.aburaagetarou.agetarouuniqueguns.utils.CSUtilities;
import com.github.aburaagetarou.agetarouuniqueguns.weapons.HurtfulSpine;
import com.github.aburaagetarou.agetarouuniqueguns.weapons.HurtlessSpine;
import me.DeeCaaD.CrackShotPlus.API;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@CommandAlias("agetarouuniqueguns|aug")
@Description("AgetarouUniqueGuns Command")
public class AUGCommand extends BaseCommand {

	public AUGCommand() {
		AgetarouUniqueGuns.addCommand(this);
	}

	@Dependency
	private AgetarouUniqueGuns plugin;

	@HelpCommand
	@CommandPermission("agetarouuniqueguns.help")
	public void onHelp(CommandSender sender, CommandHelp help) {
		help.showHelp();
	}

	@Subcommand("reload")
	@CommandPermission("agetarouuniqueguns.reload")
	@Description("設定情報を再読み込みします。")
	public void onReload(CommandSender sender) {

		plugin.loadDefaultWeaponConfig();
		sender.sendMessage("設定情報を再読み込みしました");
	}

	@Subcommand("ncmanual")
	@CommandPermission("agetarouuniqueguns.ncmanual")
	@Description("名前変更手順を送ります。")
	public void onManual(CommandSender sender) {
		// メッセージの送信のみ
		sender.sendMessage("§e=== NamechangeManual §e===");
		sender.sendMessage("§b1.§fネームド作成");
		sender.sendMessage("§b2.§fAUGプラグイン側の名前変更元のweaponsをコピー、onEnableにリスナー登録");
		sender.sendMessage("§b3.§fコピーしたもののjavaクラスの武器名の部分をcsのweaponIDに変更");
		sender.sendMessage("§b4.§fplugins/AUG/weapons/AUG_NAME_CHANGE.ymlに登録");
	}

	@Subcommand("recovery list")
	@CommandPermission("agetarouuniqueguns.recovery")
	@Description("復旧待ちアイテムを表示します。")
	public void onRecoveryList(CommandSender sender) {
		List<WeaponRecoveryStore.RecoveryEntry> entries = plugin.getWeaponRecoveryStore().list();
		if (entries.isEmpty()) {
			sender.sendMessage("§a復旧待ちアイテムはありません。");
			return;
		}

		sender.sendMessage("§e=== 復旧待ちアイテム: " + entries.size() + "件 ===");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		for (WeaponRecoveryStore.RecoveryEntry entry : entries) {
			ItemStack item = entry.getItem();
			String itemName = item.getType().name();
			if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
				itemName = item.getItemMeta().getDisplayName();
			}

			sender.sendMessage("§f" + entry.getId()
					+ " §7| §b" + entry.getPlayerName()
					+ " §7| §f" + itemName + " x" + item.getAmount()
					+ " §7| " + format.format(new Date(entry.getCreatedAt()))
					+ " §7| " + entry.getReason());
		}
	}

	@Subcommand("recovery give")
	@CommandPermission("agetarouuniqueguns.recovery")
	@Description("復旧待ちアイテムを元のプレイヤーへ付与します。")
	public void onRecoveryGive(CommandSender sender, String recoveryId) {
		WeaponRecoveryStore.RecoveryEntry entry = plugin.getWeaponRecoveryStore().get(recoveryId);
		if (entry == null) {
			sender.sendMessage("§c復旧IDが見つかりません: " + recoveryId);
			return;
		}

		Player target = Bukkit.getPlayer(entry.getPlayerUuid());
		if (target == null || !target.isOnline()) {
			sender.sendMessage("§c対象プレイヤーがオフラインです: " + entry.getPlayerName());
			return;
		}

		int slot = target.getInventory().firstEmpty();
		if (slot < 0) {
			sender.sendMessage("§c対象プレイヤーのインベントリに空きがありません。");
			return;
		}

		target.getInventory().setItem(slot, entry.getItem());
		if (!plugin.getWeaponRecoveryStore().remove(entry.getId())) {
			target.getInventory().setItem(slot, null);
			sender.sendMessage("§c復旧ログの更新に失敗したため、付与を中止しました。");
			return;
		}

		plugin.getLogger().info("[WeaponRecovery] delivered id=" + entry.getId()
				+ " player=" + target.getName() + " operator=" + sender.getName());
		sender.sendMessage("§a復旧アイテムを " + target.getName() + " へ付与しました: " + entry.getId());
		target.sendMessage("§a運営から復旧アイテムが付与されました。");
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
