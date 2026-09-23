package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class AugActionBarManager {

    public static final int PRIORITY_STREAK = 10;
    public static final int PRIORITY_MESSAGE = 50;
    public static final int PRIORITY_BAR = 60;

    private final JavaPlugin plugin;
    private final Function<String, String> colorizer;

    private final Map<UUID, String> textMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> untilMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> priorityMap = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> taskMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bypassMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> suppressedUntilMap = new ConcurrentHashMap<>();

    public AugActionBarManager(JavaPlugin plugin, Function<String, String> colorizer) {
        this.plugin = plugin;
        this.colorizer = colorizer;
        setupProtocolLibGuard();
    }

    public void send(Player player, String message, int ticks, int priority) {
        if (player == null || message == null || message.isEmpty() || ticks <= 0) return;

        UUID uuid = player.getUniqueId();
        if (isSuppressed(uuid)) return;

        long now = System.currentTimeMillis();
        long currentUntil = untilMap.getOrDefault(uuid, 0L);
        int currentPriority = priorityMap.getOrDefault(uuid, 0);

        if (currentUntil > now && priority < currentPriority) {
            return;
        }

        textMap.put(uuid, colorizer.apply(message));
        untilMap.put(uuid, now + (ticks * 50L));
        priorityMap.put(uuid, priority);

        if (taskMap.containsKey(uuid)) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stop(uuid);
                    return;
                }

                if (isSuppressed(uuid)) {
                    return;
                }

                long until = untilMap.getOrDefault(uuid, 0L);
                if (until <= System.currentTimeMillis()) {
                    stop(uuid);
                    return;
                }

                String text = textMap.get(uuid);
                if (text == null || text.isEmpty()) {
                    stop(uuid);
                    return;
                }

                bypassMap.merge(uuid, 1, Integer::sum);
                player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(text)
                );
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
        taskMap.put(uuid, task);
    }

    public void stop(UUID uuid) {
        textMap.remove(uuid);
        untilMap.remove(uuid);
        priorityMap.remove(uuid);
        bypassMap.remove(uuid);
        suppressedUntilMap.remove(uuid);

        BukkitRunnable task = taskMap.remove(uuid);
        if (task != null) task.cancel();
    }

    public void stop(Player player) {
        if (player != null) {
            stop(player.getUniqueId());
        }
    }

    public boolean isActive(Player player) {
        if (player == null) return false;
        return untilMap.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    public void suppress(Player player, int ticks) {
        if (player == null || ticks <= 0) return;

        UUID uuid = player.getUniqueId();
        long until = System.currentTimeMillis() + (ticks * 50L);
        suppressedUntilMap.merge(uuid, until, Math::max);

        textMap.remove(uuid);
        untilMap.remove(uuid);
        priorityMap.remove(uuid);
        bypassMap.remove(uuid);

        BukkitRunnable task = taskMap.remove(uuid);
        if (task != null) task.cancel();
    }

    private boolean isSuppressed(UUID uuid) {
        long until = suppressedUntilMap.getOrDefault(uuid, 0L);
        if (until > System.currentTimeMillis()) return true;

        suppressedUntilMap.remove(uuid);
        return false;
    }

    private void setupProtocolLibGuard() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();

        manager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.SYSTEM_CHAT,
                PacketType.Play.Server.SET_ACTION_BAR_TEXT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isActionBarPacket(event)) return;

                Player player = event.getPlayer();
                if (player == null) return;

                UUID uuid = player.getUniqueId();

                if (consumeBypass(uuid)) {
                    return;
                }

                long until = untilMap.getOrDefault(uuid, 0L);
                if (until > System.currentTimeMillis()) {
                    event.setCancelled(true);
                }
            }
        });
    }

    private boolean isActionBarPacket(PacketEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SET_ACTION_BAR_TEXT) {
            return true;
        }

        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT) {
            try {
                return Boolean.TRUE.equals(event.getPacket().getBooleans().readSafely(0));
            } catch (Exception ignored) {
                return false;
            }
        }

        return false;
    }

    private boolean consumeBypass(UUID uuid) {
        Integer count = bypassMap.get(uuid);
        if (count == null || count <= 0) return false;

        if (count <= 1) {
            bypassMap.remove(uuid);
        } else {
            bypassMap.put(uuid, count - 1);
        }

        return true;
    }
}
