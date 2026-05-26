package com.github.aburaagetarou.agetarouuniqueguns.weapons;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class StreakParticleEffect {

    public static void play(JavaPlugin plugin, Player player, ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("Enable", false)) return;

        String particleName = sec.getString("Particle", "FLAME");
        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        String shape = sec.getString("Shape", "CIRCLE").toUpperCase();
        int duration = Math.max(1, sec.getInt("Duration_Ticks", 40));
        int period = Math.max(1, sec.getInt("Period_Ticks", 2));
        boolean follow = sec.getBoolean("Follow_Player", true);

        Location fixedCenter = player.getLocation().clone();
        fixedCenter.add(0, sec.getDouble("Y_Offset", 1.0), 0);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                Location center;
                if (follow) {
                    center = player.getLocation().clone();
                    center.add(0, sec.getDouble("Y_Offset", 1.0), 0);
                } else {
                    center = fixedCenter.clone();
                }

                spawnShape(player, center, particle, shape, sec, tick);

                tick += period;
                if (tick >= duration) cancel();
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    private static void spawnShape(Player player, Location center, Particle particle, String shape,
                                   ConfigurationSection sec, int tick) {
        switch (shape) {
            case "POINT":
                spawn(center, particle, sec);
                break;
            case "CIRCLE":
                spawnCircle(center, particle, sec, tick);
                break;
            case "SPHERE":
                spawnSphere(center, particle, sec);
                break;
            case "HELIX":
                spawnHelix(center, particle, sec, tick);
                break;
            case "BURST":
                spawnBurst(center, particle, sec);
                break;
            default:
                spawnCircle(center, particle, sec, tick);
                break;
        }
    }

    private static void spawnCircle(Location center, Particle particle, ConfigurationSection sec, int tick) {
        double radius = sec.getDouble("Radius", 1.5);
        int points = Math.max(4, sec.getInt("Points", 24));
        double rotate = Math.toRadians(sec.getDouble("Rotate_Degrees_Per_Tick", 0.0) * tick);

        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + rotate;
            Location loc = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            spawn(loc, particle, sec);
        }
    }

    private static void spawnSphere(Location center, Particle particle, ConfigurationSection sec) {
        double radius = sec.getDouble("Radius", 1.5);
        int points = Math.max(8, sec.getInt("Points", 48));

        for (int i = 0; i < points; i++) {
            double y = 1.0 - (2.0 * i / (points - 1));
            double r = Math.sqrt(1.0 - y * y);
            double angle = Math.PI * (3.0 - Math.sqrt(5.0)) * i;

            Location loc = center.clone().add(
                    Math.cos(angle) * r * radius,
                    y * radius,
                    Math.sin(angle) * r * radius
            );
            spawn(loc, particle, sec);
        }
    }

    private static void spawnHelix(Location center, Particle particle, ConfigurationSection sec, int tick) {
        double radius = sec.getDouble("Radius", 1.2);
        double height = sec.getDouble("Height", 2.0);
        int points = Math.max(4, sec.getInt("Points", 24));
        double turns = sec.getDouble("Turns", 2.0);
        double rotate = Math.toRadians(sec.getDouble("Rotate_Degrees_Per_Tick", 12.0) * tick);

        for (int i = 0; i < points; i++) {
            double progress = (double) i / points;
            double angle = Math.PI * 2.0 * turns * progress + rotate;
            Location loc = center.clone().add(
                    Math.cos(angle) * radius,
                    progress * height - height / 2.0,
                    Math.sin(angle) * radius
            );
            spawn(loc, particle, sec);
        }
    }

    private static void spawnBurst(Location center, Particle particle, ConfigurationSection sec) {
        spawn(center, particle, sec);
    }

    private static void spawn(Location loc, Particle particle, ConfigurationSection sec) {
        World world = loc.getWorld();
        if (world == null) return;

        int count = Math.max(0, sec.getInt("Count", 1));
        double offsetX = sec.getDouble("Offset_X", 0.0);
        double offsetY = sec.getDouble("Offset_Y", 0.0);
        double offsetZ = sec.getDouble("Offset_Z", 0.0);
        double extra = sec.getDouble("Extra", 0.0);

        if (particle == Particle.REDSTONE) {
            int r = clamp(sec.getInt("Red", 255));
            int g = clamp(sec.getInt("Green", 255));
            int b = clamp(sec.getInt("Blue", 255));
            float size = (float) sec.getDouble("Size", 1.0);
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(r, g, b), size);
            world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra, dust);
        } else {
            world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}