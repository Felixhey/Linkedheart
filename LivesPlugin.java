package felix.lives;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LivesPlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, UUID> linked = new HashMap<>();

    private final String HEART_3 = "\uE001";
    private final String HEART_2 = "\uE002";
    private final String HEART_1 = "\uE003";
    private final String LINKED_HEART = "\uE004";

    private File livesFile;
    private org.bukkit.configuration.file.FileConfiguration livesConfig;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        getCommand("lives").setExecutor(this);
        getCommand("linked").setExecutor(this);
        getCommand("checklink").setExecutor(this);
        getCommand("revive").setExecutor(this);

        setupConfig();
        loadData();
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void setupConfig() {
        livesFile = new File(getDataFolder(), "lives.yml");
        if (!livesFile.exists()) {
            livesFile.getParentFile().mkdirs();
            saveResource("lives.yml", false);
        }
        livesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(livesFile);
    }

    private void loadData() {
        if (livesConfig.isConfigurationSection("players")) {
            for (String uuidStr : livesConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                int hearts = livesConfig.getInt("players." + uuidStr + ".lives", 3);
                String link = livesConfig.getString("players." + uuidStr + ".linked", null);
                lives.put(uuid, hearts);
                if (link != null) {
                    linked.put(uuid, UUID.fromString(link));
                }
            }
        }
    }

    private void saveData() {
        for (UUID uuid : lives.keySet()) {
            livesConfig.set("players." + uuid + ".lives", lives.get(uuid));
            UUID partner = linked.get(uuid);
            if (partner != null) {
                livesConfig.set("players." + uuid + ".linked", partner.toString());
            } else {
                livesConfig.set("players." + uuid + ".linked", null);
            }
        }
        try {
            livesConfig.save(livesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player killer)) return;

        Player victim = e.getEntity();
        UUID vId = victim.getUniqueId();

        int current = lives.getOrDefault(vId, 3);

        if (current > 0) {
            current--;
            lives.put(vId, current);
        } else {
            if (linked.containsKey(vId)) {
                victim.sendMessage(ChatColor.DARK_RED + "Dein " + LINKED_HEART + " wurde zerstört!");
                banPlayer(victim);
                return;
            }
        }

        showHearts(victim);

        if (current <= 0 && !linked.containsKey(vId)) {
            victim.sendMessage(ChatColor.RED + "Du hast alle Herzen verloren! Dein " + LINKED_HEART + " ist nun aktiv.");
        }

        saveData();
    }

    private void banPlayer(Player p) {
        String msg = ChatColor.RED + "Dein LinkedHeart wurde zerstört. Du bist permanent gebannt!";
        Bukkit.getBanList(BanList.Type.NAME).addBan(p.getName(), msg, null, "System");
        p.kickPlayer(msg);
    }

    private void showHearts(Player p) {
        int current = lives.getOrDefault(p.getUniqueId(), 3);
        String display = switch (current) {
            case 3 -> HEART_3;
            case 2 -> HEART_2;
            case 1 -> HEART_1;
            default -> linked.containsKey(p.getUniqueId()) ? LINKED_HEART : "";
        };
        p.sendActionBar(ChatColor.GOLD + "Deine Leben: " + display);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("lives")) {
            if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || !target.hasPlayedBefore() && !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Bitte eine Zahl (1-3) angeben.");
                    return true;
                }
                amount = Math.max(1, Math.min(3, amount));
                lives.put(target.getUniqueId(), amount);

                if (target.isOnline()) {
                    showHearts((Player) target);
                }

                sender.sendMessage(ChatColor.GREEN + "Leben gesetzt: " + target.getName() + " -> " + amount);
                saveData();
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("linked")) {
            if (args.length == 2) {
                Player p1 = Bukkit.getPlayer(args[0]);
                Player p2 = Bukkit.getPlayer(args[1]);
                if (p1 == null || p2 == null) {
                    sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                    return true;
                }
                if (linked.containsKey(p1.getUniqueId()) || linked.containsKey(p2.getUniqueId())) {
                    sender.sendMessage(ChatColor.RED + "Einer der Spieler ist bereits verlinkt.");
                    return true;
                }
                linked.put(p1.getUniqueId(), p2.getUniqueId());
                linked.put(p2.getUniqueId(), p1.getUniqueId());

                p1.sendMessage(ChatColor.AQUA + "Du bist nun mit " + p2.getName() + " verlinkt " + LINKED_HEART);
                p2.sendMessage(ChatColor.AQUA + "Du bist nun mit " + p1.getName() + " verlinkt " + LINKED_HEART);

                saveData();
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("checklink")) {
            if (!(sender instanceof Player player)) return true;
            if (!linked.containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Du bist nicht verlinkt.");
                return true;
            }
            if (lives.getOrDefault(player.getUniqueId(), 0) > 0) {
                player.sendMessage(ChatColor.RED + "Du kannst den Link erst sehen, wenn dein LinkedHeart aktiv ist.");
                return true;
            }
            UUID partnerId = linked.get(player.getUniqueId());
            OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
            if (partner != null) {
                player.sendMessage(ChatColor.GREEN + "Dein Partner ist: " + partner.getName());
            } else {
                player.sendMessage(ChatColor.YELLOW + "Dein Partner ist offline.");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("revive")) {
            if (args.length == 1) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                    return true;
                }

                Bukkit.getBanList(BanList.Type.NAME).pardon(target.getName());
                lives.put(target.getUniqueId(), 3);

                if (linked.containsKey(target.getUniqueId())) {
                    UUID partner = linked.get(target.getUniqueId());
                    linked.remove(partner);
                    linked.remove(target.getUniqueId());
                }

                sender.sendMessage(ChatColor.GREEN + "Spieler " + target.getName() + " wurde revived (3 Leben, kein Link).");

                if (target.isOnline()) {
                    showHearts((Player) target);
                    ((Player) target).sendMessage(ChatColor.GOLD + "Du wurdest von einem Admin revived!");
                }

                saveData();
                return true;
            }
        }

        return false;
    }
}
