package com.urlcustomdiscs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Oraxen mode: instead of maintaining a separate resource pack, custom disc sounds are written into
 * Oraxen's pack folder (plugins/Oraxen/pack/assets/urlcustomdiscs/...) and Oraxen regenerates,
 * hosts and sends the merged pack ({@code oraxen reload pack}).
 *
 * <p>The sounds live in their own namespace so they never collide with the sounds.json Oraxen
 * generates for minecraft:/oraxen:. Sound event: {@code urlcustomdiscs:<disc_name>}.
 */
public class OraxenPackManager {
    public static final String NAMESPACE = "urlcustomdiscs";

    private final URLCustomDiscs plugin;
    private final URLCustomDiscs.OS os;

    public OraxenPackManager(URLCustomDiscs plugin, URLCustomDiscs.OS os) {
        this.plugin = plugin;
        this.os = os;
    }

    /** Sound event id for a disc in Oraxen mode (resource locations must be lowercase). */
    public static String soundKey(String discName) {
        return NAMESPACE + ":" + discName.toLowerCase(Locale.ROOT);
    }

    private File namespaceFolder() {
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        return new File(new File(pluginsFolder, plugin.getOraxenPackFolder()), "assets/" + NAMESPACE);
    }

    public void createCustomDisc(Player player, String mp3FileName, String discName, String audioType, JSONObject discInfo) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FFmpegManager ffmpegManager = new FFmpegManager(plugin, os);
            File oggFile = new File(plugin.getTempAudioFolder(), discName + ".ogg");
            boolean converted = ffmpegManager.convertAudioWithFFmpeg(mp3FileName, oggFile, audioType);

            File mp3File = new File(plugin.getTempAudioFolder(), mp3FileName);
            if (mp3File.exists()) mp3File.delete();

            if (!converted || !oggFile.exists()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Failed to convert the MP3 audio to Ogg Vorbis using FFmpeg."));
                return;
            }

            try {
                addSound(oggFile, discName);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to add the disc to the Oraxen pack: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Error adding the custom disc to the Oraxen pack."));
                return;
            } finally {
                if (oggFile.exists()) oggFile.delete();
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.GREEN + "Audio converted and added to the Oraxen pack. Updating the resource pack...");
                reloadOraxenPack();
                DiscFactory.giveCustomDiscToPlayer(player, discInfo);
            });
        });
    }

    public void deleteCustomDisc(Player player, String discName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                removeSound(discName);
                new DiscJsonManager(plugin).deleteDisc(discName);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to remove the disc from the Oraxen pack: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Error deleting the custom disc from the Oraxen pack."));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.GREEN + "Custom disc " + ChatColor.GOLD + discName + ChatColor.GREEN + " deleted. Updating the resource pack...");
                reloadOraxenPack();
            });
        });
    }

    private synchronized void addSound(File oggFile, String discName) throws IOException {
        String name = discName.toLowerCase(Locale.ROOT);
        File folder = namespaceFolder();
        File soundsFolder = new File(folder, "sounds");
        Files.createDirectories(soundsFolder.toPath());
        Files.copy(oggFile.toPath(), new File(soundsFolder, name + ".ogg").toPath(), StandardCopyOption.REPLACE_EXISTING);

        JSONObject sounds = readSoundsJson(folder);
        JSONObject entry = new JSONObject();
        entry.put("category", "record");
        JSONArray list = new JSONArray();
        JSONObject sound = new JSONObject();
        sound.put("name", NAMESPACE + ":" + name);
        sound.put("stream", true);
        list.put(sound);
        entry.put("sounds", list);
        sounds.put(name, entry);
        Files.writeString(new File(folder, "sounds.json").toPath(), sounds.toString(2));
    }

    private synchronized void removeSound(String discName) throws IOException {
        String name = discName.toLowerCase(Locale.ROOT);
        File folder = namespaceFolder();
        File ogg = new File(folder, "sounds/" + name + ".ogg");
        if (ogg.exists() && !ogg.delete()) throw new IOException("Could not delete " + ogg);
        JSONObject sounds = readSoundsJson(folder);
        if (sounds.has(name)) {
            sounds.remove(name);
            Files.writeString(new File(folder, "sounds.json").toPath(), sounds.toString(2));
        }
    }

    private JSONObject readSoundsJson(File folder) throws IOException {
        File json = new File(folder, "sounds.json");
        if (!json.exists()) return new JSONObject();
        return new JSONObject(Files.readString(json.toPath()));
    }

    /** Lets Oraxen rebuild, upload and resend its pack, which now includes the disc sounds. Main thread only. */
    public void reloadOraxenPack() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Oraxen")) {
            plugin.getLogger().warning("Oraxen is not enabled; the disc sounds were written but the pack was not reloaded.");
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), plugin.getOraxenReloadCommand());
    }
}
