package me.ayunami2000.ayunEagVidMap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class Main extends JavaPlugin implements CommandExecutor, Listener {
    //todo: add queue command + store audio loc world & dont send to players in other worlds

    private VideoMapPacketCodecBukkit videoMapCodec = null;
    private Vector audioLoc = new Vector(0, 100, 0);
    private String url = "";

    @Override
    public void onEnable(){
        this.saveDefaultConfig();
        this.getCommand("ayunvid").setExecutor(this);
        audioLoc.setX(this.getConfig().getDouble("audio.x"));
        audioLoc.setY(this.getConfig().getDouble("audio.y"));
        audioLoc.setZ(this.getConfig().getDouble("audio.z"));
        setSize(this.getConfig().getInt("width"), this.getConfig().getInt("width"));
        url = this.getConfig().getString("url");
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, this::syncToAllPlayers, 10000, 10000); // sync every 10 seconds
    }

    @Override
    public void onDisable(){
        videoMapCodec.disableVideoBukkit();
    }

    private void syncToPlayer(Player player) {
        VideoMapPacketCodecBukkit.nativeSendPacketToPlayer(player, videoMapCodec.syncPlaybackWithPlayersBukkit());
    }

    private void syncToAllPlayers() {
        for (Player player : this.getServer().getOnlinePlayers()) {
            syncToPlayer(player);
        }
    }

    private void sendToAllPlayers(VideoMapPacketCodecBukkit.VideoMapPacket p) {
        for (Player player : this.getServer().getOnlinePlayers()) {
            VideoMapPacketCodecBukkit.nativeSendPacketToPlayer(player, p);
        }
    }

    private void setSize(int width, int height) {
        int[][] mapIds = new int[height][width];
        int offset = this.getConfig().getInt("offset");
        for (int y = 0; y < mapIds.length; y++) {
            for (int x = 0; x < mapIds[y].length; x++) {
                mapIds[y][x] = offset++;
            }
        }
        videoMapCodec = new VideoMapPacketCodecBukkit(mapIds, audioLoc.getX(), audioLoc.getY(), audioLoc.getZ(), 0.5f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        syncToPlayer(event.getPlayer());
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
                url = args[1];
                sendToAllPlayers(videoMapCodec.beginPlaybackBukkit(url, true, Integer.MAX_VALUE / 1000.0f));
                sender.sendMessage("seturl");
                break;
            case "l":
            case "loc":
            case "location":
                if (args.length < 4) {
                    sender.sendMessage("not enough args, using current location...");
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("you are not in game! you must specify the coordinates to use this command from console...");
                        break;
                    }
                    audioLoc = ((Player) sender).getLocation().toVector();
                } else {
                    double x,y,z;
                    try {
                        x = Double.parseDouble(args[1]);
                        y = Double.parseDouble(args[2]);
                        z = Double.parseDouble(args[3]);
                    } catch(NumberFormatException e) {
                        sender.sendMessage("one or more of the provided arguments is not a number!");
                        break;
                    }
                    audioLoc.setX(x);
                    audioLoc.setY(y);
                    audioLoc.setZ(z);
                }
                syncToAllPlayers();
                sender.sendMessage("set location of audio");
                break;
            case "p":
            case "play":
            case "pause":
                sender.sendMessage("resuming & loading if needed, or pausing");
                if (videoMapCodec.isPaused()) {
                    sendToAllPlayers(videoMapCodec.beginPlaybackBukkit(url, true, Integer.MAX_VALUE / 1000.0f));
                } else {
                    sendToAllPlayers(videoMapCodec.setPausedBukkit(true));
                }
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
                sendToAllPlayers(videoMapCodec.disableVideoBukkit());
                setSize(width, height);
                syncToAllPlayers();
                sender.sendMessage("set width & height");
                break;
            default:
                sender.sendMessage("invalid");
        }
        return true;
    }
}
