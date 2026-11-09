package dev.blockshuffle;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Block Shuffle (Fabric mod).
 * Every round each player gets a random block and has 5 minutes to stand on it.
 * Rounds repeat until someone runs /blockstop.
 * Server-side only: players without the mod can still join a host who has it.
 */
public class BlockShuffleMod implements ModInitializer {

    // ---- Settings (edit these, then rebuild) ----
    private static final int ROUND_SECONDS = 300;      // 5 minutes per round
    private static final int BREAK_SECONDS = 10;       // pause between rounds
    private static final int ROUNDS_PER_TIER = 3;      // rounds before blocks get harder
    private static final boolean TELL_EVERYONE_ON_FINISH = false; // true = announce to all

    // ---- Block pools (only blocks reachable in the Overworld) ----
    private static final List<Block> EASY = List.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.COBBLESTONE,
            Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
            Blocks.SAND, Blocks.GRAVEL, Blocks.SANDSTONE, Blocks.CRAFTING_TABLE,
            Blocks.FURNACE, Blocks.GLASS, Blocks.WHITE_WOOL, Blocks.COAL_ORE);

    private static final List<Block> MEDIUM = List.of(
            Blocks.IRON_ORE, Blocks.COPPER_BLOCK, Blocks.COAL_BLOCK, Blocks.BRICKS,
            Blocks.STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.HAY_BLOCK, Blocks.TNT,
            Blocks.CLAY, Blocks.TERRACOTTA, Blocks.SNOW_BLOCK, Blocks.ICE,
            Blocks.PUMPKIN, Blocks.MELON, Blocks.BOOKSHELF, Blocks.DEEPSLATE,
            Blocks.PODZOL, Blocks.MYCELIUM, Blocks.SMOOTH_STONE, Blocks.NOTE_BLOCK);

    private static final List<Block> HARD = List.of(
            Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK,
            Blocks.OBSIDIAN, Blocks.EMERALD_ORE, Blocks.DIAMOND_ORE, Blocks.DIAMOND_BLOCK,
            Blocks.PRISMARINE, Blocks.SEA_LANTERN, Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK);

    // ---- Game state ----
    private MinecraftServer server;
    private boolean running = false;
    private boolean inBreak = false;
    private int round = 0;
    private int elapsedTicks = 0;
    private int breakTicksLeft = 0;

    private final Map<UUID, Block> targets = new LinkedHashMap<>();
    private final Set<UUID> finished = new HashSet<>();
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Integer> wins = new HashMap<>();
    private final Map<UUID, Integer> losses = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("blockstart")
                    .executes(ctx -> startGame(ctx.getSource())));
            dispatcher.register(CommandManager.literal("blockstop")
                    .executes(ctx -> stopGame(ctx.getSource())));
        });
        ServerTickEvents.END_SERVER_TICK.register(srv -> tick());
    }

    // ---------------- Game flow ----------------

    private int startGame(ServerCommandSource source) {
        if (running) {
            source.sendError(Text.literal("The challenge is already running."));
            return 0;
        }
        MinecraftServer srv = source.getServer();
        if (srv.getPlayerManager().getPlayerList().isEmpty()) {
            source.sendError(Text.literal("No players online."));
            return 0;
        }
        server = srv;
        running = true;
        round = 0;
        wins.clear();
        losses.clear();
        participants.clear();
        broadcast(Text.literal("=== BLOCK SHUFFLE STARTED ===").formatted(Formatting.GOLD));
        broadcast(Text.literal("Stand on your block before time runs out. Use /blockstop to end.")
                .formatted(Formatting.YELLOW));
        beginRound();
        return 1;
    }

    private int stopGame(ServerCommandSource source) {
        if (!running) {
            source.sendError(Text.literal("The challenge is not running."));
            return 0;
        }
        running = false;
        inBreak = false;
        targets.clear();
        finished.clear();
        broadcast(Text.literal("=== BLOCK SHUFFLE STOPPED ===").formatted(Formatting.GOLD));
        printScores("Final scores");
        return 1;
    }

    private void beginRound() {
        round++;
        inBreak = false;
        elapsedTicks = 0;
        targets.clear();
        finished.clear();

        int tier = Math.min(2, (round - 1) / ROUNDS_PER_TIER);
        List<Block> pool = new ArrayList<>(EASY);
        if (tier >= 1) pool.addAll(MEDIUM);
        if (tier >= 2) pool.addAll(HARD);
        Collections.shuffle(pool, random);

        int i = 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            Block block = pool.get(i % pool.size()); // different blocks per player where possible
            i++;
            UUID id = p.getUuid();
            targets.put(id, block);
            participants.add(id);
            names.put(id, p.getName().getString());

            p.sendMessage(Text.literal("Round " + round + ": stand on ").formatted(Formatting.AQUA)
                    .append(block.getName().formatted(Formatting.GREEN))
                    .append(Text.literal(" within " + (ROUND_SECONDS / 60) + " minutes!")
                            .formatted(Formatting.AQUA)), false);
            p.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("Round " + round + " - stand on this block").formatted(Formatting.GRAY)));
            p.networkHandler.sendPacket(new TitleS2CPacket(block.getName().formatted(Formatting.GREEN)));
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void tick() {
        if (!running || server == null) return;

        if (inBreak) {
            breakTicksLeft--;
            if (breakTicksLeft > 0 && breakTicksLeft % 20 == 0) {
                Text msg = Text.literal("Next round in " + (breakTicksLeft / 20) + "s")
                        .formatted(Formatting.YELLOW);
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.sendMessage(msg, true);
                }
            }
            if (breakTicksLeft <= 0) beginRound();
            return;
        }

        elapsedTicks++;

        if (elapsedTicks % 10 == 0) checkPlayers();
        if (elapsedTicks % 20 == 0) updateActionBar();

        // End the round when BOTH conditions are satisfied as an alternative:
        // either every active player has completed their target, OR the full
        // five-minute timer has expired. One player's early completion alone
        // must never advance the round.
        boolean everyoneDone = !targets.isEmpty() && finished.size() >= targets.size();
        boolean timeExpired = elapsedTicks >= ROUND_SECONDS * 20;
        if (everyoneDone || timeExpired) endRound();
    }

    private void checkPlayers() {
        for (Map.Entry<UUID, Block> e : targets.entrySet()) {
            UUID id = e.getKey();
            if (finished.contains(id)) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;

            if (p.isOnGround() && standingOn(p) == e.getValue()) {
                finished.add(id);
                wins.merge(id, 1, Integer::sum);
                int secs = elapsedTicks / 20;
                String time = (secs / 60) + ":" + String.format("%02d", secs % 60);
                p.sendMessage(Text.literal("You made it! Stood on ").formatted(Formatting.GREEN)
                        .append(e.getValue().getName().formatted(Formatting.GREEN))
                        .append(Text.literal(" at " + time + ".").formatted(Formatting.GREEN)), false);
                if (TELL_EVERYONE_ON_FINISH) {
                    broadcast(Text.literal(names.getOrDefault(id, "?") + " finished their block!")
                            .formatted(Formatting.GRAY));
                }
            }
        }
    }

    private void updateActionBar() {
        int left = Math.max(0, ROUND_SECONDS - elapsedTicks / 20);
        String t = (left / 60) + ":" + String.format("%02d", left % 60);
        for (Map.Entry<UUID, Block> e : targets.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null) continue;
            if (finished.contains(e.getKey())) {
                p.sendMessage(Text.literal("Done! Waiting for others... " + t)
                        .formatted(Formatting.GREEN), true);
            } else {
                p.sendMessage(e.getValue().getName()
                        .append(Text.literal("  |  " + t))
                        .formatted(left <= 30 ? Formatting.RED : Formatting.GOLD), true);
            }
        }
    }

    private void endRound() {
        broadcast(Text.literal("--- Round " + round + " over ---").formatted(Formatting.GOLD));
        for (Map.Entry<UUID, Block> e : targets.entrySet()) {
            UUID id = e.getKey();
            if (!finished.contains(id)) {
                losses.merge(id, 1, Integer::sum);
                broadcast(Text.literal(names.getOrDefault(id, "?") + " failed to stand on ")
                        .formatted(Formatting.RED)
                        .append(e.getValue().getName().formatted(Formatting.RED))
                        .append(Text.literal(".").formatted(Formatting.RED)));
            }
        }
        printScores("Scores");
        inBreak = true;
        breakTicksLeft = BREAK_SECONDS * 20;
    }

    // ---------------- Helpers ----------------

    private Block standingOn(ServerPlayerEntity p) {
        BlockPos pos = BlockPos.ofFloored(p.getX(), p.getY() - 0.1, p.getZ());
        return p.getWorld().getBlockState(pos).getBlock();
    }

    private void printScores(String title) {
        broadcast(Text.literal(title + ":").formatted(Formatting.YELLOW));
        for (UUID id : participants) {
            broadcast(Text.literal(" " + names.getOrDefault(id, "?") + ": "
                    + wins.getOrDefault(id, 0) + " passed, "
                    + losses.getOrDefault(id, 0) + " failed").formatted(Formatting.WHITE));
        }
    }

    private void broadcast(Text text) {
        server.getPlayerManager().broadcast(text, false);
    }
}
