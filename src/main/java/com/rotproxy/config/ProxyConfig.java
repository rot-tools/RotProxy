package com.rotproxy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rotproxy.RotProxyMod;
import com.rotproxy.config.ProxyProfile;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import net.fabricmc.loader.api.FabricLoader;

public class ProxyConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("rotproxy_profiles.json");
    private static List<ProxyProfile> profiles = new ArrayList<ProxyProfile>();
    private static int activeProfileIndex = 0;
    private static boolean enabled = false;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ProxyProfile createBlankProfile(String name) {
        ProxyProfile profile = new ProxyProfile(name);
        profile.host = "";
        profile.port = 0;
        profile.username = "";
        profile.password = "";
        profile.type = ProxyProfile.ProxyType.SOCKS5;
        return profile;
    }

    private static String getMachineSalt() {
        try {
            byte[] mac;
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            if (network != null && (mac = network.getHardwareAddress()) != null) {
                StringBuilder sb = new StringBuilder();
                for (byte b : mac) {
                    sb.append(String.format("%02X", b));
                }
                return sb.toString();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return "RotProxyDefaultSalt2024";
    }

    private static SecretKey deriveKey(String salt) throws Exception {
        byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec("RotProxyKey".toCharArray(), saltBytes, 65536, 256);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    public static String encrypt(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            SecretKey key = ProxyConfig.deriveKey(ProxyConfig.getMachineSalt());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            Arrays.fill(iv, (byte)82);
            cipher.init(1, (Key)key, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        }
        catch (Exception e) {
            throw new IllegalStateException("Could not encrypt proxy credentials; config was not saved", e);
        }
    }

    public static String decrypt(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            SecretKey key = ProxyConfig.deriveKey(ProxyConfig.getMachineSalt());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            Arrays.fill(iv, (byte)82);
            cipher.init(2, (Key)key, new IvParameterSpec(iv));
            byte[] decoded = Base64.getDecoder().decode(value);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            return "";
        }
    }

    public static void load() {
        profiles.clear();
        enabled = false;
        activeProfileIndex = 0;
        if (!Files.exists(CONFIG_PATH, new LinkOption[0])) {
            profiles.add(ProxyConfig.createBlankProfile("Profile 1"));
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH);){
            JsonObject root = (JsonObject)GSON.fromJson((Reader)reader, JsonObject.class);
            activeProfileIndex = root.has("activeProfile") ? root.get("activeProfile").getAsInt() : 0;
            enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
            JsonArray arr = root.getAsJsonArray("profiles");
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                ProxyProfile p = new ProxyProfile();
                p.name = obj.get("name").getAsString();
                p.host = obj.get("host").getAsString();
                p.port = obj.get("port").getAsInt();
                p.username = ProxyConfig.decrypt(obj.has("username") ? obj.get("username").getAsString() : "");
                p.password = ProxyConfig.decrypt(obj.has("password") ? obj.get("password").getAsString() : "");
                p.type = ProxyProfile.ProxyType.valueOf(obj.get("type").getAsString());
                profiles.add(p);
            }
        }
        catch (Exception e) {
            RotProxyMod.LOGGER.error("Failed to load RotProxy config; proxy remains disabled ({})", e.getClass().getSimpleName());
            profiles.clear();
            enabled = false;
            profiles.add(ProxyConfig.createBlankProfile("Profile 1"));
        }
        if (profiles.isEmpty()) {
            profiles.add(ProxyConfig.createBlankProfile("Profile 1"));
        }
        if (activeProfileIndex < 0 || activeProfileIndex >= profiles.size()) {
            activeProfileIndex = 0;
        }
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("activeProfile", (Number)activeProfileIndex);
            root.addProperty("enabled", Boolean.valueOf(enabled));
            JsonArray arr = new JsonArray();
            for (ProxyProfile p : profiles) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", p.name);
                obj.addProperty("host", p.host);
                obj.addProperty("port", (Number)p.port);
                obj.addProperty("username", ProxyConfig.encrypt(p.username));
                obj.addProperty("password", ProxyConfig.encrypt(p.password));
                obj.addProperty("type", p.type.name());
                arr.add((JsonElement)obj);
            }
            root.add("profiles", (JsonElement)arr);
            Files.createDirectories(CONFIG_PATH.getParent());
            Path pending = CONFIG_PATH.resolveSibling("rotproxy_profiles.json.tmp");
            try (BufferedWriter w = Files.newBufferedWriter(pending, new OpenOption[0]);){
                GSON.toJson((JsonElement)root, (Appendable)w);
            }
            try {
                Files.move(pending, CONFIG_PATH, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (Exception e) {
            RotProxyMod.LOGGER.error("Failed to save RotProxy config", (Throwable)e);
        }
    }

    public static List<ProxyProfile> getProfiles() {
        return profiles;
    }

    public static int getActiveProfileIndex() {
        return activeProfileIndex;
    }

    public static void setActiveProfileIndex(int i) {
        activeProfileIndex = i;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static ProxyProfile getActiveProfile() {
        if (profiles.isEmpty()) {
            return null;
        }
        if (activeProfileIndex < 0 || activeProfileIndex >= profiles.size()) {
            return profiles.get(0);
        }
        return profiles.get(activeProfileIndex);
    }

    public static void addProfile(String name) {
        profiles.add(ProxyConfig.createBlankProfile(name));
    }

    public static void removeProfile(int index) {
        if (profiles.size() > 1) {
            profiles.remove(index);
            if (index < activeProfileIndex) activeProfileIndex--;
            if (activeProfileIndex >= profiles.size()) {
                activeProfileIndex = profiles.size() - 1;
            }
        }
    }

    public static void clearProfile(int index) {
        if (index < 0 || index >= profiles.size()) {
            return;
        }
        String name = ProxyConfig.profiles.get((int)index).name;
        profiles.set(index, ProxyConfig.createBlankProfile((String)(name == null || name.isBlank() ? "Profile " + (index + 1) : name.trim())));
    }

    public static void resetAllProfiles() {
        profiles.clear();
        profiles.add(ProxyConfig.createBlankProfile("Profile 1"));
        activeProfileIndex = 0;
        enabled = false;
    }
}
