/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.espi.protectionstones.commands;

import dev.espi.protectionstones.*;
import dev.espi.protectionstones.compat.FoliaScheduler;
import dev.espi.protectionstones.compat.CompatTask;
import dev.espi.protectionstones.utils.ChatUtil;
import dev.espi.protectionstones.utils.UUIDCache;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ArgTp implements PSCommandArg {

    private static ConcurrentHashMap<UUID, Integer> waitCounter = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<UUID, CompatTask> taskCounter = new ConcurrentHashMap<>();

    // /ps tp, /ps home

    @Override
    public List<String> getNames() {
        return Collections.singletonList("tp");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Collections.singletonList("protectionstones.tp");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        Player p = (Player) s;

        // preliminary checks
        if (!p.hasPermission("protectionstones.tp"))
            return PSL.msg(p, PSL.NO_PERMISSION_TP.msg());

        if (args.length < 2 || args.length > 3)
            return PSL.msg(p, PSL.TP_HELP.msg());

        if (args.length == 2) { // /ps tp [name/id]
            final org.bukkit.World snapshotWorld = p.getWorld();
            FoliaScheduler.callGlobal(() -> ProtectionStones.getPSRegions(snapshotWorld, args[1]))
                .thenAccept(regions -> {
                    if (regions.isEmpty()) {
                        PSL.msg(p, PSL.REGION_DOES_NOT_EXIST.msg());
                        return;
                    }
                    if (regions.size() > 1) {
                        FoliaScheduler.runEntity(p, () -> ChatUtil.displayDuplicateRegionAliases(p, regions));
                        return;
                    }
                    FoliaScheduler.runEntity(p, () -> teleportPlayer(p, regions.get(0)));
                });
        } else { // /ps tp [player] [num]
            // get the region id the player wants to teleport to
            int regionNumber;
            try {
                regionNumber = Integer.parseInt(args[2]);
                if (regionNumber <= 0) {
                    return PSL.msg(p, PSL.NUMBER_ABOVE_ZERO.msg());
                }
            } catch (NumberFormatException e) {
                return PSL.msg(p, PSL.TP_VALID_NUMBER.msg());
            }

            String tpName = args[1];
            // region checks, and set lp to offline player
            if (!UUIDCache.containsName(tpName)) {
                return PSL.msg(p, PSL.PLAYER_NOT_FOUND.msg());
            }
            UUID tpUuid = UUIDCache.getUUIDFromName(tpName);

            // run region search on global thread
            final org.bukkit.World snapshotWorld = p.getWorld();
            FoliaScheduler.callGlobal(() -> PSPlayer.fromUUID(tpUuid).getPSRegionsCrossWorld(snapshotWorld, false))
                .thenAccept(regions -> {
                    // check if region was found
                    if (regions.isEmpty()) {
                        PSL.msg(p, PSL.REGION_NOT_FOUND_FOR_PLAYER.msg()
                                .replace("%player%", tpName));
                        return;
                    } else if (regionNumber > regions.size()) {
                        PSL.msg(p, PSL.ONLY_HAS_REGIONS.msg()
                                .replace("%player%", tpName)
                                .replace("%num%", "" + regions.size()));
                        return;
                    }

                    FoliaScheduler.runEntity(p, () -> teleportPlayer(p, regions.get(regionNumber - 1)));
                });
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return null;
    }

    static void teleportPlayer(Player p, PSRegion r) {
        if (r.getTypeOptions() == null) {
            PSL.msg(p, ChatColor.RED + "This region is problematic, and the block type (" + r.getType() + ") is not configured. Please contact an administrator.");
            Bukkit.getLogger().info(ChatColor.RED + "This region is problematic, and the block type (" + r.getType() + ") is not configured.");
            return;
        }

        // teleport player
        if (r.getTypeOptions().tpWaitingSeconds == 0 || p.hasPermission("protectionstones.tp.bypasswait")) {
            // no teleport delay
            PSL.msg(p, PSL.TPING.msg());
            FoliaScheduler.teleportPlayer(p, r.getHome());
        } else if (!r.getTypeOptions().noMovingWhenTeleportWaiting) {
            // teleport delay, but doesn't care about moving
            p.sendMessage(PSL.TP_IN_SECONDS.msg().replace("%seconds%", "" + r.getTypeOptions().tpWaitingSeconds));

            long delayTicks = 20L * r.getTypeOptions().tpWaitingSeconds;
            FoliaScheduler.runEntityLater(p, () -> {
                PSL.msg(p, PSL.TPING.msg());
                FoliaScheduler.teleportPlayer(p, r.getHome());
            }, null, delayTicks);

        } else {// delay and not allowed to move
            PSL.msg(p, PSL.TP_IN_SECONDS.msg().replace("%seconds%", "" + r.getTypeOptions().tpWaitingSeconds));
            UUID uuid = p.getUniqueId();

            // remove queued teleport if already running
            if (taskCounter.get(uuid) != null) removeUUIDTimer(uuid);

            // add teleport wait tasks to queue
            waitCounter.put(uuid, 0);

            Location startLocation = p.getLocation().clone();

            taskCounter.put(uuid, FoliaScheduler.runEntityTimer(p, () -> {
                        if (waitCounter.get(uuid) == null) {
                            removeUUIDTimer(uuid);
                            return;
                        }
                        waitCounter.put(uuid, waitCounter.get(uuid) + 1);

                        Location currentLocation = p.getLocation();
                        ProtectionStones.getInstance().debug(String.format("Checking player movement. Player location: (%.2f, %.2f, %.2f), actual location: (%.2f, %.2f, %.2f)", currentLocation.getX(), currentLocation.getY(), currentLocation.getZ(), startLocation.getX(), startLocation.getY(), startLocation.getZ()));

                        if (!inThreshold(startLocation.getX(), currentLocation.getX()) || !inThreshold(startLocation.getY(), currentLocation.getY()) || !inThreshold(startLocation.getZ(), currentLocation.getZ())) {
                            ProtectionStones.getInstance().debug(String.format("Not in threshold. X check: %s, Y check: %s, Z check: %s", inThreshold(startLocation.getX(), currentLocation.getX()), inThreshold(startLocation.getY(), currentLocation.getY()), inThreshold(startLocation.getZ(), currentLocation.getZ())));
                            PSL.msg(p, PSL.TP_CANCELLED_MOVED.msg());
                            removeUUIDTimer(uuid);
                        } else if (waitCounter.get(uuid) == r.getTypeOptions().tpWaitingSeconds * 4) {
                            PSL.msg(p, PSL.TPING.msg());
                            FoliaScheduler.teleportPlayer(p, r.getHome());
                            removeUUIDTimer(uuid);
                        }
                    }, () -> {
                        waitCounter.remove(uuid);
                        taskCounter.remove(uuid);
                    }, 5L, 5L));
        }
    }

    private static boolean inThreshold(double location, double playerLoc) {
        return playerLoc <= location + 1.0 && playerLoc >= location - 1.0;
    }

    private static void removeUUIDTimer(UUID uuid) {
        if (taskCounter.get(uuid) != null) taskCounter.get(uuid).cancel();
        waitCounter.remove(uuid);
        taskCounter.remove(uuid);
    }
}
