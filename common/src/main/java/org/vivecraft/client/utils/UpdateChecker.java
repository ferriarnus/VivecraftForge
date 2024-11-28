package org.vivecraft.client.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.server.config.ServerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class UpdateChecker {

    public static boolean HAS_UPDATE = false;

    public static String CHANGELOG = "";

    public static String NEWEST_VERSION = "";

    public static boolean checkForUpdates() {
        VRSettings.LOGGER.info("Vivecraft: Checking for Updates");

        char updateType;
        if (Xplat.isDedicatedServer()) {
            // server
            updateType = ServerConfig.CHECK_FOR_UPDATE_TYPE.get().charAt(0);
        } else {
            // client
            updateType = switch (ClientDataHolderVR.getInstance().vrSettings.updateType) {
                case RELEASE -> 'r';
                case BETA -> 'b';
                case ALPHA -> 'a';
            };
        }

        try {
            String apiURL = "https://api.modrinth.com/v2/project/vivecraft/version?loaders=[%22" + Xplat.getModloader().name + "%22]&game_versions=[%22" + SharedConstants.VERSION_STRING + "%22]";
            HttpURLConnection conn = (HttpURLConnection) new URL(apiURL).openConnection();
            // 10 seconds read and connect timeout
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json,*/*");
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                VRSettings.LOGGER.error("Vivecraft: Error '{}' fetching updates", conn.getResponseCode());
                return false;
            }

            JsonElement j = JsonParser.parseString(inputStreamToString(conn.getInputStream()));

            List<Version> versions = new LinkedList<>();

            if (j.isJsonArray()) {
                for (JsonElement element : j.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        JsonObject obj = element.getAsJsonObject();
                        versions.add(
                            new Version(obj.get("name").getAsString(),
                                obj.get("version_number").getAsString(),
                                obj.get("changelog").getAsString()));
                    }
                }
            }
            // sort the versions, modrinth doesn't guarantee them to be sorted.
            Collections.sort(versions);

            String currentVersionNumber = Xplat.getModVersion() + "-" + Xplat.getModloader().name;
            Version current = new Version(currentVersionNumber, currentVersionNumber, "");

            // enforce update notifications if using a non release
            if (current.alpha > 0 && updateType != 'a') {
                updateType = 'a';
            } else if (current.beta > 0 && updateType != 'a') {
                updateType = 'b';
            }

            for (Version v : versions) {
                if (v.isVersionType(updateType) && current.compareTo(v) > 0) {
                    CHANGELOG += "§a" + v.fullVersion + "§r" + ": \n" + v.changelog + "\n\n";
                    if (NEWEST_VERSION.isEmpty()) {
                        NEWEST_VERSION = v.fullVersion;
                    }
                    HAS_UPDATE = true;
                }
            }
            // no carriage returns please
            CHANGELOG = CHANGELOG.replaceAll("\\r", "");
            if (HAS_UPDATE) {
                VRSettings.LOGGER.info("Vivecraft update found: {}", NEWEST_VERSION);
            }
        } catch (IOException e) {
            VRSettings.LOGGER.error("Vivecraft: fetching available vivecraft updates: ", e);
        }
        return HAS_UPDATE;
    }

    private static String inputStreamToString(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream))
            .lines().collect(Collectors.joining("\n"));
    }

    private static class Version implements Comparable<Version> {

        public String fullVersion;

        public String changelog;

        public int major;
        public int minor;
        public int patch;
        int alpha = 0;
        int beta = 0;
        boolean featureTest = false;

        public Version(String version, String version_number, String changelog) {
            this.fullVersion = version;
            this.changelog = changelog;
            String[] parts = version_number.split("-");
            int viveVersionIndex = parts.length - 2;
            // parts should be [mc version]-(pre/rc)-[vive version]-(vive a/b/test)-[mod loader]
            if (!parts[viveVersionIndex].contains(".")) {
                viveVersionIndex = parts.length - 3;
                String testString = parts[parts.length - 2];
                // prerelease
                if (testString.matches("a\\d+.*")) {
                    this.alpha = Integer.parseInt(testString.replaceAll("\\D+", ""));
                } else if (testString.matches("b\\d+.*")) {
                    this.beta = Integer.parseInt(testString.replaceAll("\\D+", ""));
                }
                // if the prerelease string is not just aXX or bXX it's a feature test as well and ranked slightly higher
                if (!testString.replaceAll("^[ab]\\d+", "").isEmpty()) {
                    this.featureTest = true;
                }
            }
            String[] ints = parts[viveVersionIndex].split("\\.");
            // remove all letters, since stupid me put a letter in one version
            this.major = Integer.parseInt(ints[0].replaceAll("\\D+", ""));
            this.minor = Integer.parseInt(ints[1].replaceAll("\\D+", ""));
            this.patch = Integer.parseInt(ints[2].replaceAll("\\D+", ""));
        }

        @Override
        public int compareTo(UpdateChecker.Version o) {
            long result = this.compareNumber() - o.compareNumber();
            if (result < 0) {
                return 1;
            } else if (result == 0L) {
                return 0;
            }
            return -1;
        }

        public boolean isVersionType(char versionType) {
            return switch (versionType) {
                case 'r' -> this.beta == 0 && this.alpha == 0 && !this.featureTest;
                case 'b' -> this.beta >= 0 && this.alpha == 0 && !this.featureTest;
                case 'a' -> this.alpha >= 0 && !this.featureTest;
                default -> false;
            };
        }

        // two digits per segment, should be enough right?
        private long compareNumber() {
            // digit flag
            // major minor patch full release beta alpha feature test
            // 00    00    00    0            00   00    0
            return (this.featureTest ? 1L : 0L) +
                this.alpha * 10L +
                this.beta * 1000L +
                (this.alpha + this.beta == 0 ? 10000L : 0L) +
                this.patch * 1000000L +
                this.minor * 100000000L +
                this.major * 10000000000L;
        }
    }
}
