package me.ayunami2000.ayunEagVidMap;

import net.minecraft.server.v1_5_R3.Packet;
import net.minecraft.server.v1_5_R3.Packet131ItemData;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_5_R3.entity.CraftPlayer;
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

    private VideoMapPacketCodec videoMapCodec = null;
    private Vector audioLoc = new Vector(0, 100, 0);
    private String url = "";
    private boolean urlChanged = true;
    private final int[] mapSize = {1, 1};
    private int mapSizeCap = 10;
    private int mapOffset = 0;
    private int interval = 10;
    private int syncTask = -1;
    private boolean imageMode = false;

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
        if (videoMapCodec.mapIds != null) sendToAllPlayers(videoMapCodec.disableVideo());
    }

    private void stopSyncTask() {
        if (syncTask != -1) {
            this.getServer().getScheduler().cancelTask(syncTask);
            syncTask = -1;
        }
    }

    private void createSyncTask() {
        if (interval > 0) syncTask = this.getServer().getScheduler().scheduleSyncRepeatingTask(this, this::syncToAllPlayers, 0, interval * 20L);
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
        interval = this.getConfig().getInt("interval");
        imageMode = this.getConfig().getBoolean("image");
        stopSyncTask();
        createSyncTask();
    }

    private void syncToPlayer(Player player) {
        freePacketSender(player, videoMapCodec.syncPlaybackWithPlayers());
    }

    private void syncToAllPlayers() {
        sendToAllPlayers(videoMapCodec.syncPlaybackWithPlayers());
    }

    private void sendToAllPlayers(byte[] p) {
        for (Player player : this.getServer().getOnlinePlayers()) freePacketSender(player, p);
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
        videoMapCodec = new VideoMapPacketCodec(mapIds, audioLoc.getX(), audioLoc.getY(), audioLoc.getZ(), 0.5f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (videoMapCodec != null && videoMapCodec.getURL() != null) {
            Player player = event.getPlayer();
            freePacketSender(player, videoMapCodec.beginPlayback(videoMapCodec.getURL() == null ? "" : videoMapCodec.getURL(), videoMapCodec.isLoopEnable(), videoMapCodec.getDuration()));
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
            case "m":
            case "mode":
                sendToAllPlayers(videoMapCodec.disableVideo());
                imageMode = !imageMode;
                sendToAllPlayers(videoMapCodec.beginPlayback(videoMapCodec.getURL() == null ? "" : videoMapCodec.getURL(), videoMapCodec.isLoopEnable(), videoMapCodec.getDuration()));
                this.getConfig().set("image", imageMode);
                this.saveConfig();
                MessageHandler.sendPrefixedMessage(sender, "mode", imageMode ? MessageHandler.getMessage("image") : MessageHandler.getMessage("video"));
                break;
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
                sendToAllPlayers(videoMapCodec.moveAudioSource(audioLoc.getX(), audioLoc.getY(), audioLoc.getZ(), 0.5f));
                sendToAllPlayers(videoMapCodec.setPlaybackTime(ct));
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
                        sendToAllPlayers(videoMapCodec.beginPlayback(url, true, Integer.MAX_VALUE / 1000.0f));
                    } else {
                        MessageHandler.sendPrefixedMessage(sender, "resuming");
                    }
                    sendToAllPlayers(videoMapCodec.setPaused(false));
                } else {
                    MessageHandler.sendPrefixedMessage(sender, "pausing");
                    sendToAllPlayers(videoMapCodec.setPaused(true));
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
                sendToAllPlayers(videoMapCodec.disableVideo());
                setSize(width, height);
                syncToAllPlayers();
                this.getConfig().set("size.width", mapSize[0]);
                this.getConfig().set("size.height", mapSize[1]);
                this.saveConfig();
                MessageHandler.sendPrefixedMessage(sender, "setSize", mapSize[0], mapSize[1], mapSize[0] * mapSize[1]);
                break;
            case "i":
            case "int":
            case "interval":
                if (args.length < 2) {
                    MessageHandler.sendPrefixedMessage(sender, "currentInterval", interval);
                    break;
                }
                try {
                    interval = Math.max(0, Integer.parseInt(args[1]));
                    stopSyncTask();
                    createSyncTask();
                    MessageHandler.sendPrefixedMessage(sender, "setInterval");
                } catch (NumberFormatException e) {
                    MessageHandler.sendPrefixedMessage(sender, "notANumber", args[1], "integer");
                }
                break;
            default:
                MessageHandler.sendPrefixedMessage(sender, "invalidUsage");
        }
        return true;
    }

    private void freePacketSender(Player player, byte[] packet) {
        nativeSendPacketToPlayer(player, new Packet131ItemData((short)(104 + (imageMode ? 1 : 0)), (short)0, packet));
    }

    private static void nativeSendPacketToPlayer(Player player, Object obj) {
        if(obj == null) {
            return;
        }
        ((CraftPlayer)player).getHandle().playerConnection.sendPacket((Packet)obj);
    }
}
