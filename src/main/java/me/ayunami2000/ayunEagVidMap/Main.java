package me.ayunami2000.ayunEagVidMap;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements CommandExecutor, Listener {
    private boolean playing = false;
    private VideoMapPacketCodecBukkit videoMapCodec = null;
    private int[][] mapIds;
    private Location audioLoc;
    private String url;

    @Override
    public void onEnable(){
        this.saveDefaultConfig();
        this.getCommand("ayunvid").setExecutor(this);
        int width = this.getConfig().getInt("width");
        int height = this.getConfig().getInt("width");
        mapIds = new int[height][width];
        int offset = this.getConfig().getInt("offset");
        for (int y = 0; y < mapIds.length; y++) {
            for (int x = 0; x < mapIds[y].length; x++) {
                mapIds[y][x] = offset++;
            }
        }
        audioLoc.setX(this.getConfig().getDouble("audio.x"));
        audioLoc.setY(this.getConfig().getDouble("audio.y"));
        audioLoc.setZ(this.getConfig().getDouble("audio.z"));
        videoMapCodec = new VideoMapPacketCodecBukkit(mapIds, audioLoc.getX(), audioLoc.getY(), audioLoc.getZ(), 0.5f);
        url = this.getConfig().getString("url");
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player player : this.getServer().getOnlinePlayers()) {
                VideoMapPacketCodecBukkit.nativeSendPacketToPlayer(player, videoMapCodec.syncPlaybackWithPlayersBukkit());
            }
        }, 10000, 10000); // sync every 10 seconds
    }

    @Override
    public void onDisable(){
        videoMapCodec.disableVideoBukkit();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (playing) {
            VideoMapPacketCodecBukkit.nativeSendPacketToPlayer(event.getPlayer(), videoMapCodec.);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0){
            sender.sendMessage("usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "u":
            case "url":
                if (args.length < 2) {
                    sender.sendMessage("no url specified!");
                    break;
                }
                this.getConfig().set("url", args[1]);
                this.saveConfig();
                sender.sendMessage("seturl");
                break;
            case "l":
            case "loc":
            case "location":
                sender.sendMessage("set location of audio");
                break;
            case "p":
            case "play":
            case "pause":
                sender.sendMessage("resuming & loading if needed, or pausing");

                new VideoMapPacketCodecBukkit()
                break;
            case "s":
            case "size":
                if (args.length < 3) {
                    sender.sendMessage("must specify width & height to set! current vals are...");
                    break;
                }
                int width;
                int height;
                try {
                    width = Math.max(1, Integer.parseInt(args[1]));
                    height = Math.max(1, Integer.parseInt(args[2]));
                } catch(NumberFormatException e) {
                    sender.sendMessage("");
                    break;
                }
                this.getConfig().set("width", width);
                this.getConfig().set("height", height);
                this.saveConfig();
                sender.sendMessage("set width & height");
                break;
            default:
                sender.sendMessage("invalid");
        }
        return true;
    }
}
