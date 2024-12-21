package org.vivecraft.client.network;

import net.minecraft.world.entity.player.Player;
import org.apache.commons.io.IOUtils;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client_vr.settings.VRSettings;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SupporterReceiver {
    private static final Object LOCK = new Object();
    private static final List<Player> QUEUED_PLAYERS = new LinkedList<>();
    private static Map<String, Integer> CACHE;
    private static boolean DOWNLOAD_STARTED;
    private static boolean DOWNLOAD_FAILED;

    private static void fileDownloadFinished(String url, String data, boolean addData) {
        synchronized (LOCK) {
            if (data != null) {
                try {
                    Map<String, Integer> userMap = new HashMap<>();
                    if (addData) {
                        userMap = CACHE;
                    }

                    String[] lines = data.split("\\r?\\n");

                    for (String user : lines) {
                        if (user.isEmpty()) {
                            continue;
                        }
                        try {
                            String[] bits = user.split(":");
                            int i = Integer.parseInt(bits[1]);
                            userMap.put(bits[0].toLowerCase(), i);

                            for (Player player : QUEUED_PLAYERS) {
                                if (bits[0].equalsIgnoreCase(player.getGameProfile().getName())) {
                                    ClientVRPlayers.getInstance().setHMD(player.getUUID(), i);
                                }
                            }
                        } catch (Exception e) {
                            VRSettings.LOGGER.error("Vivecraft: error with supporters txt: {}", user,  e);
                        }
                    }

                    CACHE = userMap;
                } catch (Exception e) {
                    VRSettings.LOGGER.error("Vivecraft: error parsing supporter data: {}", url, e);
                    DOWNLOAD_FAILED = true;
                }
            } else {
                DOWNLOAD_FAILED = true;
            }
        }
    }

    public static void addPlayerInfo(Player p) {
        if (!DOWNLOAD_FAILED) {
            synchronized (LOCK) {
                if (CACHE == null) {
                    QUEUED_PLAYERS.add(p);
                    ClientVRPlayers.getInstance().setHMD(p.getUUID(), 0);

                    if (!DOWNLOAD_STARTED) {
                        DOWNLOAD_STARTED = true;
                        String ogSupportersUrl = "https://www.vivecraft.org/patreon/current.txt";
                        String viveModSupportersUrl = "https://raw.githubusercontent.com/Vivecraft/VivecraftSupporters/supporters/supporters.txt";
                        new Thread(() -> {
                            try {
                                String ogSupporters = IOUtils.toString(new URL(ogSupportersUrl), StandardCharsets.UTF_8);
                                String viveModSupporters = IOUtils.toString(new URL(viveModSupportersUrl), StandardCharsets.UTF_8);
                                fileDownloadFinished(ogSupportersUrl, ogSupporters, false);
                                fileDownloadFinished(viveModSupportersUrl, viveModSupporters, true);
                                synchronized (LOCK) {
                                    QUEUED_PLAYERS.clear();
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }).start();
                    }
                } else {
                    ClientVRPlayers.getInstance().setHMD(p.getUUID(), CACHE.getOrDefault(p.getGameProfile().getName().toLowerCase(), 0));
                }
            }
        }
    }
}
