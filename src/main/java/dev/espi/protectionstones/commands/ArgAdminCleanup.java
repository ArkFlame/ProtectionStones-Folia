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

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.compat.FoliaScheduler;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ArgAdminCleanup {

    private static final class PreviewState {
        private final File previewFile;
        private final FileWriter previewFileOutputStream;
        private final AtomicInteger pendingPreviewWrites = new AtomicInteger();
        private final AtomicBoolean cleanupFinished = new AtomicBoolean(false);
        private final AtomicBoolean previewFinalized = new AtomicBoolean(false);

        private PreviewState(File previewFile, FileWriter previewFileOutputStream) {
            this.previewFile = previewFile;
            this.previewFileOutputStream = previewFileOutputStream;
        }
    }

    private static final class CleanupPlan {
        private final boolean removeOperation;
        private final List<PSRegion> regions;
        private final PreviewState previewState;
        private final String headerMessage;

        private CleanupPlan(boolean removeOperation, List<PSRegion> regions, PreviewState previewState, String headerMessage) {
            this.removeOperation = removeOperation;
            this.regions = regions;
            this.previewState = previewState;
            this.headerMessage = headerMessage;
        }
    }

    // /ps admin cleanup [remove/preview]
    static boolean argumentAdminCleanup(CommandSender p, String[] preParseArgs) {
        if (preParseArgs.length < 3 || !Arrays.asList("remove", "preview").contains(preParseArgs[2].toLowerCase())) {
            PSL.msg(p, ArgAdmin.getCleanupHelp());
            return true;
        }

        String cleanupOperation = preParseArgs[2].toLowerCase(); // [remove|preview]

        World w;
        String alias = null;

        List<String> args = new ArrayList<>();

        // determine if there is an alias flag selected, and remove [-t typealias] if there is
        for (int i = 3; i < preParseArgs.length; i++) {
            if (preParseArgs[i].equals("-t") && i != preParseArgs.length-1) {
                alias = preParseArgs[++i];
            } else {
                args.add(preParseArgs[i]);
            }
        }

        // the args array should consist of: [days, world (optional)]
        if (args.size() > 1 && Bukkit.getWorld(args.get(1)) != null) {
            w = Bukkit.getWorld(args.get(1));
        } else {
            if (p instanceof Player) {
                w = ((Player) p).getWorld();
            } else {
                PSL.msg(p, args.size() > 1 ? PSL.INVALID_WORLD.msg() : PSL.ADMIN_CONSOLE_WORLD.msg());
                return true;
            }
        }

        // create preview file
        PreviewState previewState = null;
        if (cleanupOperation.equals("preview")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd H-m-s");
            File previewFile = new File(ProtectionStones.getInstance().getDataFolder().getAbsolutePath() + "/" + LocalDateTime.now().format(formatter) + " cleanup preview.txt");
            try {
                previewFile.createNewFile();
                previewState = new PreviewState(previewFile, new FileWriter(previewFile));
            } catch (IOException e) {
                e.printStackTrace();
                PSL.msg(p, ChatColor.RED + "Internal error, please check the console logs.");
                return true;
            }
        }

        RegionManager rgm = WGUtils.getRegionManagerWithWorld(w);
        Map<String, ProtectedRegion> regions = rgm.getRegions();

        // async cleanup task
        String finalAlias = alias;
        final CommandSender commandSender = p;
        final PreviewState finalPreviewState = previewState;
        final int daysValue = (args.size() > 0) ? Integer.parseInt(args.get(0)) : 30;
        
        FoliaScheduler.callGlobal(() -> {
            HashSet<UUID> activePlayers = new HashSet<>();

            // loop over offline players and add to list if they haven't joined recently
            for (OfflinePlayer op : Bukkit.getServer().getOfflinePlayers()) {
                long lastPlayed = (System.currentTimeMillis() - op.getLastPlayed()) / 86400000L;
                try {
                    // a player is active if they have joined within the days
                    if (lastPlayed < daysValue) {
                        activePlayers.add(op.getUniqueId());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // loop over all regions in global phase and find regions to delete
            List<PSRegion> toDelete = new ArrayList<>();
            for (String regionId : regions.keySet()) {
                PSRegion r = PSRegion.fromWGRegion(w, regions.get(regionId));
                if (r == null) { // not a ps region (unconfigured types still count as ps regions)
                    continue;
                }

                // if an alias is specified, skip regions that aren't of the type
                if (finalAlias != null && (r.getTypeOptions() == null || !r.getTypeOptions().alias.equals(finalAlias))) {
                    continue;
                }

                long numOfActiveOwners = r.getOwners().stream().filter(activePlayers::contains).count();
                long numOfActiveMembers = r.getMembers().stream().filter(activePlayers::contains).count();

                // remove region if there are no owners left
                if (numOfActiveOwners == 0) {
                    if (ProtectionStones.getInstance().getConfigOptions().cleanupDeleteRegionsWithMembersButNoOwners || numOfActiveMembers == 0) {
                        toDelete.add(r);
                    }
                }
            }

            String headerMessage = PSL.ADMIN_CLEANUP_HEADER.msg()
                    .replace("%arg%", cleanupOperation)
                    .replace("%days%", "" + daysValue);
            return new CleanupPlan(cleanupOperation.equalsIgnoreCase("remove"), toDelete, finalPreviewState, headerMessage);
        }).thenAccept(result -> {
            CleanupPlan plan = (CleanupPlan) result;
            Runnable completion = () -> {
                PSL.msg(commandSender, plan.headerMessage);
                regionLoop(plan.regions.iterator(), commandSender, plan.removeOperation, plan.previewState);
            };
            if (commandSender instanceof Player) {
                FoliaScheduler.runEntity((Player) commandSender, completion);
            } else {
                FoliaScheduler.runGlobal(completion);
            }
        });
        return true;
    }

    static private void regionLoop(Iterator<PSRegion> deleteRegionsIterator, CommandSender p, boolean isRemoveOperation, PreviewState previewState) {
        if (deleteRegionsIterator.hasNext()) {
            FoliaScheduler.runGlobalLater(() ->
                    processRegion(deleteRegionsIterator, p, isRemoveOperation, previewState), 1);
        } else {
            markCleanupFinished(p, isRemoveOperation, previewState);
        }
    }

    // Process a region, and then iterate to the next region on the next tick.
    // This is to prevent the server from pausing for the entire duration of the cleanup.
    // (lag from loading chunks to remove protection blocks)
    static private void processRegion(Iterator<PSRegion> deleteRegionsIterator, CommandSender p, boolean isRemoveOperation, PreviewState previewState) {
        PSRegion r = deleteRegionsIterator.next();

        if (isRemoveOperation) { // delete

            sendOnSender(p, ChatColor.YELLOW + "Removed region " + r.getId() + " due to inactive owners.");

            if (r.isHidden()) {
                FoliaScheduler.runGlobal(() -> {
                    r.deleteRegion(false);
                    regionLoop(deleteRegionsIterator, p, true, previewState);
                });
            } else {
                FoliaScheduler.runRegion(r.getProtectBlock().getLocation(), () -> {
                    r.getProtectBlock().setType(Material.AIR);
                    FoliaScheduler.runGlobal(() -> {
                        r.deleteRegion(false);
                        regionLoop(deleteRegionsIterator, p, true, previewState);
                    });
                });
            }
            return;
        } else { // preview

            sendOnSender(p, ChatColor.YELLOW + "Found region " + r.getId() + " that can be deleted.");

            // adds region id to preview file
            previewState.pendingPreviewWrites.incrementAndGet();
            try {
                final String regionId = r.getId();
                FoliaScheduler.runAsync(() -> {
                    try {
                        synchronized (previewState.previewFileOutputStream) {
                            previewState.previewFileOutputStream.write(regionId + "\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        previewState.pendingPreviewWrites.decrementAndGet();
                        tryFinalizePreview(p, previewState);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                previewState.pendingPreviewWrites.decrementAndGet();
            }
        }

        // go to next region
        regionLoop(deleteRegionsIterator, p, isRemoveOperation, previewState);
    }

    private static void markCleanupFinished(CommandSender sender, boolean isRemoveOperation, PreviewState previewState) {
        if (previewState == null) {
            sendOnSender(sender, PSL.ADMIN_CLEANUP_FOOTER.msg().replace("%arg%", "remove"));
            return;
        }

        previewState.cleanupFinished.set(true);
        tryFinalizePreview(sender, previewState);
    }

    private static void tryFinalizePreview(CommandSender sender, PreviewState previewState) {
        if (previewState == null || previewState.cleanupFinished.get() == false) {
            return;
        }
        if (previewState.pendingPreviewWrites.get() != 0) {
            return;
        }
        if (!previewState.previewFinalized.compareAndSet(false, true)) {
            return;
        }

        FoliaScheduler.runAsync(() -> {
            try {
                previewState.previewFileOutputStream.flush();
                previewState.previewFileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            sendOnSender(sender, PSL.ADMIN_CLEANUP_FOOTER.msg().replace("%arg%", "preview"));
            sendOnSender(sender, ChatColor.YELLOW + "Dumped the list regions that can be deleted in " + previewState.previewFile.getName() + " (in the plugin folder).");
        });
    }

    private static void sendOnSender(CommandSender sender, String message) {
        if (sender instanceof Player) {
            FoliaScheduler.runEntity((Player) sender, () -> PSL.msg(sender, message));
        } else {
            FoliaScheduler.runGlobal(() -> PSL.msg(sender, message));
        }
    }
}
