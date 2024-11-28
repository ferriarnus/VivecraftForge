package org.vivecraft.client_vr.menuworlds;

import net.minecraft.SharedConstants;
import org.vivecraft.client.utils.FileUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MenuWorldDownloader {
    private static final String BASE_URL = "https://cache.techjargaming.com/vivecraft/115/";
    public static final String CUSTOM_WORLD_FOLDER = "menuworlds/custom_120";

    private static String LAST_WORLD = "";
    private static boolean INIT;
    private static Random RAND;

    public static void init() {
        if (INIT) {
            return;
        }
        RAND = new Random();
        RAND.nextInt();
        INIT = true;
    }

    public static void downloadWorld(String path) throws IOException, NoSuchAlgorithmException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        if (file.exists()) {
            String localSha1 = FileUtils.getFileChecksum(file, "SHA-1");
            String remoteSha1 = FileUtils.httpReadLine(BASE_URL + "checksum.php?file=" + path);
            if (localSha1.equals(remoteSha1)) {
                VRSettings.LOGGER.info("Vivecraft: MenuWorlds: SHA-1 matches for {}", path);
                return;
            }
        }
        VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Downloading world {}", path);
        FileUtils.httpReadToFile(BASE_URL + path, file);
    }

    public static InputStream getRandomWorld() {
        init();
        VRSettings settings = ClientDataHolderVR.getInstance().vrSettings;

        try {
            List<MenuWorldItem> worldList = new ArrayList<>();
            if (settings.menuWorldSelection == VRSettings.MenuWorld.BOTH || settings.menuWorldSelection == VRSettings.MenuWorld.CUSTOM) {
                worldList.addAll(getCustomWorlds());
            }
            if (settings.menuWorldSelection == VRSettings.MenuWorld.BOTH || settings.menuWorldSelection == VRSettings.MenuWorld.OFFICIAL || worldList.isEmpty()) {
                worldList.addAll(getOfficialWorlds());
            }

            // don't load the same world twice in a row
            if (worldList.size() > 1) {
                worldList.removeIf(world -> LAST_WORLD.equals(world.path) || (world.file != null && LAST_WORLD.equals(world.file.getPath())));
            }

            if (worldList.isEmpty()) {
                return getRandomWorldFallback();
            }

            MenuWorldItem world = getRandomWorldFromList(worldList);
            if (world != null) {
                LAST_WORLD = world.file != null ? world.file.getPath() : world.path;
            }
            return getStreamForWorld(world);
        } catch (IOException | UncheckedIOException | NoSuchAlgorithmException e) {
            VRSettings.LOGGER.error("Vivecraft: error getting random menuworld:", e);
            try {
                return getRandomWorldFallback();
            } catch (IOException | NoSuchAlgorithmException e2) {
                VRSettings.LOGGER.error("Vivecraft: error getting random menuworld fallback:", e);
                return null;
            }
        }
    }

    private static InputStream getStreamForWorld(MenuWorldItem world) throws IOException, NoSuchAlgorithmException {
        if (world.file != null) {
            VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Using world {}", world.file.getName());
            return new FileInputStream(world.file);
        } else if (world.path != null) {
            downloadWorld(world.path);
            VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Using official world {}", world.path);
            return new FileInputStream(world.path);
        } else {
            throw new IllegalArgumentException("File or path must be assigned");
        }
    }

    private static List<MenuWorldItem> getCustomWorlds() throws IOException {
        File dir = new File(CUSTOM_WORLD_FOLDER);
        if (dir.exists()) {
            return getWorldsInDirectory(dir);
        }
        return new ArrayList<>();
    }

    private static List<MenuWorldItem> getOfficialWorlds() throws IOException, UncheckedIOException {
        List<MenuWorldItem> list = new ArrayList<>();
        List<String> resultList = FileUtils.httpReadAllLines(
            BASE_URL + "menuworlds_list.php?minver=" + MenuWorldExporter.MIN_VERSION + "&maxver=" + MenuWorldExporter.VERSION + "&mcver=" + SharedConstants.VERSION_STRING);
        for (String str : resultList) {
            list.add(new MenuWorldItem("menuworlds/" + str, null));
        }
        return list;
    }

    private static InputStream getRandomWorldFallback() throws IOException, NoSuchAlgorithmException {
        VRSettings.LOGGER.info("Vivecraft: MenuWorlds: Couldn't find a world, trying random file from directory");
        File dir = new File("menuworlds");
        if (dir.exists()) {
            MenuWorldItem world = getRandomWorldFromList(getWorldsInDirectory(dir));
            if (world != null) {
                return getStreamForWorld(world);
            }
        }
        return null;
    }

    private static List<MenuWorldItem> getWorldsInDirectory(File dir) throws IOException {
        List<MenuWorldItem> worlds = new ArrayList<>();
        for (File file : dir.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".mmw"))) {
            int version = MenuWorldExporter.readVersion(file);
            if (version >= MenuWorldExporter.MIN_VERSION && version <= MenuWorldExporter.VERSION) {
                worlds.add(new MenuWorldItem(null, file));
            }
        }
        return worlds;
    }

    private static MenuWorldItem getRandomWorldFromList(List<MenuWorldItem> list) {
        if (!list.isEmpty()) {
            return list.get(RAND.nextInt(list.size()));
        }
        return null;
    }

    private record MenuWorldItem(String path, File file) {}
}
