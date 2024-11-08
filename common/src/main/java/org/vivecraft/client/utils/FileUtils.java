package org.vivecraft.client.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.commons.io.IOUtils;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.settings.VRSettings;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtils {
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 20000;

    /**
     * unpacks an asset through the Resource manager, this means a resource pack can override the file
     * @param sourcePath Path to the source file inside the mods assets
     * @param targetFile File to the destination file on disk
     * @param required if set and an error occurs, it will not be caught
     */
    public static void unpackAsset(String sourcePath, String targetFile, boolean required) {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                .getResource(new ResourceLocation("vivecraft", sourcePath));

            if (resource.isPresent()) {
                try (InputStream is = resource.get().open(); OutputStream os = new FileOutputStream(targetFile)) {
                    IOUtils.copy(is, os);
                }
            } else {
                // couldn't get asset from ResourceManager, unpack directly from jar
                unpackFile("assets/vivecraft/" + sourcePath, targetFile, required);
            }
        } catch(Exception exception) {
            handleAssetException(exception, sourcePath, required);
        }
    }

    /**
     * unpacks an asset through the Resource manager, this means a resource pack can override the file
     * @param sourcePath Path to the source file inside the mods assets
     * @param targetFolder Folder where to write file on disk, with the same sub folder structure as {@code sourcePath}
     * @param required if set and an error occurs, it will not be caught
     */
    public static void unpackAssetToFolder(String sourcePath, String targetFolder, boolean required) {
        unpackAsset(sourcePath, targetFolder + "/" + sourcePath, required);
    }

    /**
     * throws an exception or logs it depending on the value of {@code required}
     */
    private static void handleAssetException(Throwable e, String name, boolean required) {
        VRSettings.LOGGER.error("Vivecraft: Failed to unpack '{}' from jar:", name, e);
        if (required) {
            throw new RuntimeException("Vivecraft: Failed to unpack '" + name + "' from jar: " + e.getMessage(), e);
        }
    }

    /**
     * unpacks the given file at the given {@code sourcePath} to the given {@code targetFile} on disk
     * @param sourcePath Path to the source file inside the mod jar
     * @param targetFile File to the destination file on disk
     * @param required if set and an error occurs, it will not be caught
     * @return if a file was unpacked
     */
    private static boolean unpackFile(Path sourcePath, File targetFile, boolean required) {
        try {
            VRSettings.LOGGER.info("Vivecraft: Unpacking file '{}' ...", sourcePath);

            targetFile.getParentFile().mkdirs();

            Files.copy(sourcePath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            return true;
        } catch (Exception exception) {
            handleAssetException(exception, sourcePath.toString(), required);
            return false;
        }
    }

    /**
     * unpacks the given {@code sourceFile} to the given {@code targetFile} on disk
     * @param sourceFile path to the source file inside the mod jar
     * @param targetFile path to the destination file on disk
     * @param required if set and an error occurs, it will not be caught
     * @return if a file was unpacked
     */
    public static boolean unpackFile(String sourceFile, String targetFile, boolean required) {
        return unpackFile(Xplat.getJarPath().resolve(sourceFile), new File(targetFile), required);
    }

    /**
     * unpacks all files in the given {@code source} folder to the given {@code target} folder
     * @param source path to the source folder inside the mod jar
     * @param target path to the destination folder on disk
     * @return if a file was unpacked
     */
    public static boolean unpackFolder(String source, String target) {
        VRSettings.LOGGER.info("Vivecraft: Unpacking files of '{}' ...", source);

        new File(target).mkdirs();

        boolean didExtractSomething = false;

        try (Stream<Path> natives = Files.list(Xplat.getJarPath().resolve(source))) {
            for (Path file : natives.collect(Collectors.toCollection(ArrayList::new))) {
                didExtractSomething |= unpackFile(file, new File(target + "/" + file.getFileName()), false);
            }
        } catch (IOException e) {
            handleAssetException(e, source, false);
            return false;
        }

        if (!didExtractSomething) {
            VRSettings.LOGGER.warn("Vivecraft: Failed to unpack files from jar, no files");
        }
        return didExtractSomething;
    }

    /**
     * reads the first line of a text file from the given {@code url}
     * @param url url to the source file
     * @return first line in the source text file
     * @throws IOException if an error occurred connecting to the url
     * @throws UncheckedIOException if a read error occurred
     */
    public static String httpReadLine(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) (new URL(url)).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setUseCaches(false);
        conn.setDoInput(true);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line = br.readLine();
        br.close();
        conn.disconnect();
        return line;
    }

    /**
     * reads a text file from the given {@code url} and returns a list of all lines
     * @param url url to the source file
     * @return List of all lines in the source text file
     * @throws IOException if an error occurred connecting to the url
     * @throws UncheckedIOException if a read error occurred
     */
    public static List<String> httpReadAllLines(String url) throws IOException, UncheckedIOException {
        try (InputStream is = new URL(url).openStream()) {
            return IOUtils.readLines(is, StandardCharsets.UTF_8);
        }
    }

    /**
     * writes the data from the given {@code url} to the {@code file} location
     * @param url url to the source file
     * @param file File object to write to
     * @throws IOException if a write/read error occurred
     */
    public static void httpReadToFile(String url, File file) throws IOException {
        IOUtils.copy(new URL(url), file);
    }

    /**
     * generates a checksum for the given file
     * @param file      File to get the checksum for
     * @param algorithm checksum type to generate
     * @return hex string of the checksum
     * @throws IOException              if an error occurred while reading the file from disk
     * @throws NoSuchAlgorithmException if an unknown checksum algorithm was requested
     */
    public static String getFileChecksum(File file, String algorithm) throws IOException, NoSuchAlgorithmException {
        // read file
        byte[] bytes;
        try (InputStream is = new FileInputStream(file)){
            bytes = IOUtils.toByteArray(is);
        }

        // create checksum
        MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
        messageDigest.update(bytes);

        // format to a hex string
        try (Formatter formatter = new Formatter()) {
            for (byte b : messageDigest.digest()) {
                // 2 hex chars per byte
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
    }
}
