package me.ayunami2000.ayunEagVidMap;

import org.bukkit.command.BlockCommandSender;
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
    //todo: add queue command + store audio loc world & dont send to players in other worlds???
    //todo: or like detect where map is placed in world via entities and then per player play audio from the nearest one to them??
    //todo: also when holding video map play audio at player location for that player

    public static Main plugin;

    private VideoMapPacketCodecBukkit videoMapCodec = null;
    private Vector audioLoc = new Vector(0, 100, 0);
    private String url = "";
    private boolean urlChanged = true;
    private final int[] mapSize = {1, 1};
    private int mapSizeCap = 10;
    private int mapOffset = 0;

    @Override
    public void onLoad(){
        plugin = this;
    }

    @Override
    public void onEnable(){
        this.saveDefaultConfig();
        this.rlConfig();
        this.getCommand("ayunvid").setExecutor(this);
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable(){
        sendToAllPlayers(videoMapCodec.disableVideoBukkit());
    }

    private void rlConfig() {
        MessageHandler.initMessages();
        audioLoc.setX(this.getConfig().getDouble("audio.x"));
        audioLoc.setY(this.getConfig().getDouble("audio.y"));
        audioLoc.setZ(this.getConfig().getDouble("audio.z"));
        mapSizeCap = this.getConfig().getInt("size.cap");
        mapOffset = this.getConfig().getInt("offset");
        setSize(this.getConfig().getInt("size.width"), this.getConfig().getInt("size.height"));
        url = this.getConfig().getString("url");
    }

    private void syncToPlayer(Player player) {
        videoMapCodec.syncPlaybackWithPlayersBukkit().send(player);
    }

    private void syncToAllPlayers() {
        videoMapCodec.syncPlaybackWithPlayersBukkit().send(this.getServer().getOnlinePlayers());
    }

    private void sendToAllPlayers(VideoMapPacketCodecBukkit.VideoMapPacket p) {
        p.send(this.getServer().getOnlinePlayers());
    }

    private void setSize(int width, int height) {
        if (mapSizeCap > 0) {
            width = Math.min(width, mapSizeCap);
            height = Math.min(height, mapSizeCap);
        }
        mapSize[0] = width;
        mapSize[1] = height;
        int[][] mapIds = new int[height][width];
        int offset = mapOffset;
        for (int y = 0; y < mapIds.length; y++) {
            for (int x = 0; x < mapIds[y].length; x++) {
                mapIds[y][x] = offset++;
            }
        }
        videoMapCodec = new VideoMapPacketCodecBukkit(mapIds, audioLoc.getX(), audioLoc.getY(), audioLoc.getZ(), 0.5f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (videoMapCodec != null && videoMapCodec.getURL() != null) {
            Player player = event.getPlayer();
            videoMapCodec.beginPlaybackBukkit(videoMapCodec.getURL(), videoMapCodec.isLoopEnable(), videoMapCodec.getDuration()).send(player);
            syncToPlayer(player);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0){
            MessageHandler.sendPrefixedMessage(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "rl":
            case "reload":
                this.reloadConfig();
                this.rlConfig();
                MessageHandler.sendPrefixedMessage(sender, "reloaded");
                break;
            case "h":
            case "help":
                MessageHandler.sendPrefixedMessage(sender, "usage");
                break;
            case "u":
            case "url":
                if (args.length < 2) {
                    MessageHandler.sendPrefixedMessage(sender, "currentUrl", url);
                    break;
                }
                this.getConfig().set("url", args[1]);
                this.saveConfig();
                url = args[1];
                urlChanged = true;
                MessageHandler.sendPrefixedMessage(sender, "setUrl");
                break;
            case "l":
            case "loc":
            case "location":
                if (args.length < 4) {
                    if(sender instanceof Player) {
                        MessageHandler.sendPrefixedMessage(sender, "locCurrent");
                        audioLoc = ((Player) sender).getLocation().toVector();
                    } else if (sender instanceof BlockCommandSender) {
                        MessageHandler.sendPrefixedMessage(sender, "locCurrent");
                        audioLoc = ((BlockCommandSender) sender).getBlock().getLocation().toVector().clone().add(new Vector(0.5, 0.5, 0.5));
                    } else {
                        MessageHandler.sendPrefixedMessage(sender, "locFromConsole");
                        break;
                    }
                } else {
                    double x,y,z;
                    int offendingIndex = 1;
                    try {
                        x = Double.parseDouble(args[1]);
                        offendingIndex++;
                        y = Double.parseDouble(args[2]);
                        offendingIndex++;
                        z = Double.parseDouble(args[3]);
                    } catch(NumberFormatException e) {
                        MessageHandler.sendPrefixedMessage(sender, "notANumber", args[offendingIndex], MessageHandler.getMessage("double"));
                        break;
                    }
                    audioLoc.setX(x + 0.5);
                    audioLoc.setY(y + 0.5);
                    audioLoc.setZ(z + 0.5);
                }
                this.getConfig().set("audio.x", audioLoc.getX());
                this.getConfig().set("audio.y", audioLoc.getY());
                this.getConfig().set("audio.z", audioLoc.getZ());
                this.saveConfig();
                float ct = videoMapCodec.getPlaybackTime();
                sendToAllPlayers(videoMapCodec.moveAudioSourceBukkit(audioLoc.getX(), audioLoc.getY(), audioLoc.getZ(), 0.5f));
                sendToAllPlayers(videoMapCodec.setPlaybackTimeBukkit(ct));
                MessageHandler.sendPrefixedMessage(sender, "locSet", audioLoc);
                break;
            case "p":
            case "play":
            case "pause":
                if (args.length > 1 && args[1].equalsIgnoreCase("force")) urlChanged = true;
                if (urlChanged || videoMapCodec.isPaused()) {
                    if (urlChanged) {
                        urlChanged = false;
                        MessageHandler.sendPrefixedMessage(sender, "playing");
                        sendToAllPlayers(videoMapCodec.beginPlaybackBukkit(url, true, Integer.MAX_VALUE / 1000.0f));
                    } else {
                        MessageHandler.sendPrefixedMessage(sender, "resuming");
                    }
                    sendToAllPlayers(videoMapCodec.setPausedBukkit(false));
                } else {
                    MessageHandler.sendPrefixedMessage(sender, "pausing");
                    sendToAllPlayers(videoMapCodec.setPausedBukkit(true));
                }
                break;
            case "s":
            case "size":
                if (args.length < 3) {
                    MessageHandler.sendPrefixedMessage(sender, "currentSize", mapSize[0], mapSize[1], mapSize[0] * mapSize[1]);
                    break;
                }
                int width;
                int height;
                int offendingIndex = 1;
                try {
                    width = Math.max(1, Integer.parseInt(args[1]));
                    offendingIndex++;
                    height = Math.max(1, Integer.parseInt(args[2]));
                } catch(NumberFormatException e) {
                    MessageHandler.sendPrefixedMessage(sender, "notANumber", args[offendingIndex], MessageHandler.getMessage("integer"));
                    break;
                }
                sendToAllPlayers(videoMapCodec.disableVideoBukkit());
                setSize(width, height);
                syncToAllPlayers();
                this.getConfig().set("width", mapSize[0]);
                this.getConfig().set("height", mapSize[1]);
                this.saveConfig();
                MessageHandler.sendPrefixedMessage(sender, "setSize", mapSize[0], mapSize[1], mapSize[0] * mapSize[1]);
                break;
            default:
                MessageHandler.sendPrefixedMessage(sender, "invalidUsage");
        }
        return true;
    }
}
