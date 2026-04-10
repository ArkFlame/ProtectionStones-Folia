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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ArgAdminHide {

    private static final class HideTarget {
        private final PSRegion region;
        private final Location location;

        private HideTarget(PSRegion region) {
            this.region = region;
            this.location = region.getProtectBlock().getLocation();
        }
    }

    private static final class HideResult {
        private final String message;

        private HideResult(String message) {
            this.message = message;
        }
    }

    // /ps admin hide
    static boolean argumentAdminHide(CommandSender p, String[] args) {
        RegionManager mgr;
        World w;
        if (p instanceof Player) {
            mgr = WGUtils.getRegionManagerWithPlayer((Player) p);
            w = ((Player) p).getWorld();
        } else {
            if (args.length != 3) {
                PSL.msg(p, PSL.ADMIN_CONSOLE_WORLD.msg());
                return true;
            }
            if (Bukkit.getWorld(args[2]) == null) {
                PSL.msg(p, PSL.INVALID_WORLD.msg());
                return true;
            }
            w = Bukkit.getWorld(args[2]);
            mgr = WGUtils.getRegionManagerWithWorld(w);
        }

        // loop through regions that are protection stones and hide or unhide the block
        final CommandSender commandSender = p;
        final String hideAction = args[1];
        final boolean playerSender = p instanceof Player;
        FoliaScheduler.callGlobal(() -> {
            List<HideTarget> targets = new ArrayList<>();
            for (ProtectedRegion r : mgr.getRegions().values()) {
                if (ProtectionStones.isPSRegion(r)) {
                    PSRegion region = PSRegion.fromWGRegion(w, r);
                    if (region != null) {
                        targets.add(new HideTarget(region));
                    }
                }
            }

            return new Object[] { hideAction, targets, PSL.ADMIN_HIDE_TOGGLED.msg().replace("%message%", hideAction.equalsIgnoreCase("unhide") ? "unhidden" : "hidden") };
        }).thenAccept(result -> {
            Object[] data = (Object[]) result;
            final String action = (String) data[0];
            @SuppressWarnings("unchecked")
            final List<HideTarget> targets = (List<HideTarget>) data[1];
            final String message = (String) data[2];

            if (targets.isEmpty()) {
                Runnable completion = () -> PSL.msg(commandSender, message);
                if (playerSender) {
                    FoliaScheduler.runEntity((Player) commandSender, completion);
                } else {
                    FoliaScheduler.runGlobal(completion);
                }
                return;
            }

            final AtomicInteger remaining = new AtomicInteger(targets.size());
            for (HideTarget target : targets) {
                FoliaScheduler.runRegion(target.location, () -> {
                    if (action.equalsIgnoreCase("hide")) {
                        target.region.hide();
                    } else if (action.equalsIgnoreCase("unhide")) {
                        target.region.unhide();
                    }

                    if (remaining.decrementAndGet() == 0) {
                        Runnable completion = () -> PSL.msg(commandSender, message);
                        if (playerSender) {
                            FoliaScheduler.runEntity((Player) commandSender, completion);
                        } else {
                            FoliaScheduler.runGlobal(completion);
                        }
                    }
                });
            }
        });

        return true;
    }
}
