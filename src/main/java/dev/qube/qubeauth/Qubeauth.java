package dev.qube.qubeauth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Qubeauth server-side authentication starter. Compile/test against the exact 26.3 mappings before use. */
public final class Qubeauth implements ModInitializer {
    public static final String MOD_ID = "qubeauth";
    public static final int HOLD_X = 0, HOLD_Y = 82, HOLD_Z = 0;
    private static final int ORANGE = 0xEC5800, RED = 0xFF0000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Account> ACCOUNTS = new HashMap<>();
    private static Path dataFile;
    private static MinecraftServer server;

    @Override public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            server = srv;
            loadData(srv);
            ServerPlayer p = handler.player;
            Account a = ACCOUNTS.get(p.getUUID());
            Session s = new Session();
            s.previousDimension = a == null ? p.level().dimension().identifier().toString() : a.dimension;
            s.x = a == null ? p.getX() : a.x; s.y = a == null ? p.getY() : a.y; s.z = a == null ? p.getZ() : a.z;
            s.yaw = a == null ? p.getYRot() : a.yaw; s.pitch = a == null ? p.getXRot() : a.pitch;
            s.holdYaw = p.getYRot(); s.holdPitch = p.getXRot();
            SESSIONS.put(p.getUUID(), s);
            hold(p, s);
            prompt(p);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            SESSIONS.remove(handler.player.getUUID());
            saveData();
        });

        // This event runs before a normal chat message is broadcast. Always return false for locked players.
        // Never log candidate password text. Verify this callback against the exact 26.3 Fabric API build.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, player, params) -> {
            Session s = SESSIONS.get(player.getUUID());
            if (s == null || s.loggedIn) return true;
            String candidate = message.signedContent().plain();
            if (candidate == null || candidate.isEmpty()) { prompt(player); return false; }
            Account account = ACCOUNTS.get(player.getUUID());
            if (account == null) {
                byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
                String hash = hash(candidate, salt);
                ACCOUNTS.put(player.getUUID(), new Account(s.previousDimension, s.x, s.y, s.z, s.yaw, s.pitch,
                        hash, Base64.getEncoder().encodeToString(salt)));
                s.loggedIn = true;
                saveData();
                title(player, "Correct password", 0xFFFFFF, 40);
                restore(player, s);
            } else {
                byte[] salt = Base64.getDecoder().decode(account.salt);
                if (MessageDigest.isEqual(hash(candidate, salt).getBytes(StandardCharsets.UTF_8),
                        account.hash.getBytes(StandardCharsets.UTF_8))) {
                    s.loggedIn = true;
                    title(player, "Correct password", 0xFFFFFF, 40);
                    restore(player, s);
                } else {
                    // Lightning-like feedback without spawning a damaging lightning entity.
                    player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
                    title(player, "Incorrect password", RED, 60);
                    prompt(player);
                }
            }
            return false;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer p && isLocked(p)));

        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                Session s = SESSIONS.get(p.getUUID());
                if (s == null) continue;
                if (!s.loggedIn) {
                    hold(p, s); p.setInvulnerable(true); p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0;
                } else p.setInvulnerable(false);
            }
        });
    }

    public static boolean isLocked(ServerPlayer p) { Session s = SESSIONS.get(p.getUUID()); return s != null && !s.loggedIn; }
    public static boolean cancelCommand(ServerPlayer p) { return isLocked(p); }

    private static void hold(ServerPlayer p, Session s) {
        ServerLevel level = p.serverLevel();
        p.teleportTo(level, HOLD_X + 0.5, HOLD_Y, HOLD_Z + 0.5, Set.of(), s.holdYaw, s.holdPitch, false);
        p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0; p.setInvulnerable(true);
    }
    private static void restore(ServerPlayer p, Session s) {
        ServerLevel target = p.server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(s.previousDimension)));
        if (target == null) target = p.serverLevel();
        p.teleportTo(target, s.x, s.y, s.z, Set.of(), s.yaw, s.pitch, false);
        p.setInvulnerable(false);
    }
    private static void prompt(ServerPlayer p) {
        title(p, ACCOUNTS.containsKey(p.getUUID()) ? "Type password in chat to log in" : "Type password in chat to register", ORANGE, 80);
    }
    private static void title(ServerPlayer p, String text, int color, int stayTicks) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, stayTicks, 0));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal(text).setStyle(Style.EMPTY.withColor(color))));
    }
    private static String hash(String password, byte[] salt) {
        try {
            var spec = new javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 210_000, 256);
            return Base64.getEncoder().encodeToString(javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded());
        } catch (Exception e) { throw new IllegalStateException("Password hashing failed", e); }
    }
    private static synchronized void loadData(MinecraftServer srv) {
        if (dataFile != null) return;
        dataFile = srv.getServerDirectory().resolve("config").resolve("qubeauth").resolve("users.json");
        try {
            Files.createDirectories(dataFile.getParent()); if (!Files.exists(dataFile)) return;
            JsonObject root = JsonParser.parseString(Files.readString(dataFile)).getAsJsonObject();
            for (String key : root.keySet()) {
                JsonObject o = root.getAsJsonObject(key);
                ACCOUNTS.put(UUID.fromString(key), new Account(o.get("dimension").getAsString(), o.get("x").getAsDouble(),
                        o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat(),
                        o.get("hash").getAsString(), o.get("salt").getAsString()));
            }
        } catch (Exception e) { srv.getLogger().error("[Qubeauth] Could not load account database.", e); }
    }
    private static synchronized void saveData() {
        if (dataFile == null) return;
        JsonObject root = new JsonObject();
        ACCOUNTS.forEach((id, a) -> {
            JsonObject o = new JsonObject(); o.addProperty("dimension", a.dimension); o.addProperty("x", a.x);
            o.addProperty("y", a.y); o.addProperty("z", a.z); o.addProperty("yaw", a.yaw); o.addProperty("pitch", a.pitch);
            o.addProperty("hash", a.hash); o.addProperty("salt", a.salt); root.add(id.toString(), o);
        });
        try { Files.createDirectories(dataFile.getParent()); Files.writeString(dataFile, root.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); }
        catch (IOException e) { if (server != null) server.getLogger().error("[Qubeauth] Could not save account database.", e); }
    }
    private record Account(String dimension, double x, double y, double z, float yaw, float pitch, String hash, String salt) {}
    private static final class Session {
        boolean loggedIn; String previousDimension = "minecraft:overworld";
        double x, y, z; float yaw, pitch, holdYaw, holdPitch;
    }
}
