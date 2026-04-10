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

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.*;
import dev.espi.protectionstones.compat.FoliaScheduler;
import dev.espi.protectionstones.utils.WGMerge;
import dev.espi.protectionstones.utils.WGUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.World;

import java.util.*;
import java.util.stream.Collectors;

public class ArgMerge implements PSCommandArg {

    private static final class MergeResult {
        private enum Type {
            SUCCESS,
            HOLE,
            RENTED_REJECTION
        }

        private final Type type;
        private final String message;
        private final boolean reopenMenu;

        private MergeResult(Type type, String message, boolean reopenMenu) {
            this.type = type;
            this.message = message;
            this.reopenMenu = reopenMenu;
        }

        private static MergeResult success(boolean reopenMenu) {
            return new MergeResult(Type.SUCCESS, PSL.MERGE_MERGED.msg(), reopenMenu);
        }

        private static MergeResult hole() {
            return new MergeResult(Type.HOLE, PSL.NO_REGION_HOLES.msg(), false);
        }

        private static MergeResult rentedRejection(String message) {
            return new MergeResult(Type.RENTED_REJECTION, message, false);
        }
    }
    @Override
    public List<String> getNames() {
        return Arrays.asList("merge");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.merge");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    public static List<TextComponent> getGUI(Player p, PSRegion r) {
        return r.getMergeableRegions(p).stream()
                .map(psr -> {
                    TextComponent tc = new TextComponent(ChatColor.AQUA + "> " + ChatColor.WHITE + psr.getId());
                    if (psr.getName() != null) tc.addExtra(" (" + psr.getName() + ")"); // name
                    tc.addExtra(" (" + psr.getTypeOptions().alias + ")"); // region type

                    tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " merge " + r.getId() + " " + psr.getId()));
                    tc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(PSL.MERGE_CLICK_TO_MERGE.msg().replace("%region%", psr.getId())).create()));
                    return tc;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        if (!s.hasPermission("protectionstones.merge"))
            return PSL.msg(s, PSL.NO_PERMISSION_MERGE.msg());

        if (!ProtectionStones.getInstance().getConfigOptions().allowMergingRegions)
            return PSL.msg(s, PSL.MERGE_DISABLED.msg());

        Player p = (Player) s;
        if (args.length == 1) { // GUI

            PSRegion r = PSRegion.fromLocationGroup(p.getLocation());
            if (r == null)
                return PSL.msg(s, PSL.NOT_IN_REGION.msg());

            if (r.getTypeOptions() == null) {
                PSL.msg(p, ChatColor.RED + "This region is problematic, and the block type (" + r.getType() + ") is not configured. Please contact an administrator.");
                Bukkit.getLogger().info(ChatColor.RED + "This region is problematic, and the block type (" + r.getType() + ") is not configured.");
                return true;
            }

            if (!r.getTypeOptions().allowMerging)
                return PSL.msg(s, PSL.MERGE_NOT_ALLOWED.msg());

            List<TextComponent> components = getGUI(p, r);
            if (components.isEmpty()) {
                PSL.msg(p, PSL.MERGE_NO_REGIONS.msg());
            } else {
                p.sendMessage(ChatColor.WHITE + ""); // send empty line
                PSL.msg(p, PSL.MERGE_HEADER.msg().replace("%region%", r.getId()));
                PSL.msg(p, PSL.MERGE_WARNING.msg());
                for (TextComponent tc : components) p.spigot().sendMessage(tc);
                p.sendMessage(ChatColor.WHITE + ""); // send empty line
            }

        } else if (args.length == 3) { // /ps merge [region] [root]
            RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
            ProtectedRegion region = rm.getRegion(args[1]), root = rm.getRegion(args[2]);
            LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(p);

            if (!ProtectionStones.isPSRegion(region) || !ProtectionStones.isPSRegion(root))
                return PSL.msg(p, PSL.MULTI_REGION_DOES_NOT_EXIST.msg());

            if (!p.hasPermission("protectionstones.admin") && (!region.isOwner(lp) || !root.isOwner(lp)))
                return PSL.msg(p, PSL.NO_ACCESS.msg());

            // check if region is actually overlapping the region
            var overlappingRegionIds = WGUtils.findOverlapOrAdjacentRegions(root, rm, p.getWorld()).stream().map(ProtectedRegion::getId).collect(Collectors.toList());
            if (!overlappingRegionIds.contains(region.getId()))
                return PSL.msg(p, PSL.REGION_NOT_OVERLAPPING.msg());

            // check if merging is allowed in config
            PSRegion aRegion = PSRegion.fromWGRegion(p.getWorld(), region), aRoot = PSRegion.fromWGRegion(p.getWorld(), root);
            if (!aRegion.getTypeOptions().allowMerging || !aRoot.getTypeOptions().allowMerging)
                return PSL.msg(p, PSL.MERGE_NOT_ALLOWED.msg());

            // check if the region types allow for it
            if (!WGUtils.canMergeRegionTypes(aRegion.getTypeOptions(), aRoot))
                return PSL.msg(p, PSL.MERGE_NOT_ALLOWED.msg());

            Player commandPlayer = p;
            RegionManager commandRm = rm;
            World commandWorld = p.getWorld();
            FoliaScheduler.callGlobal(() -> {
                try {
                    WGMerge.mergeRealRegions(commandWorld, commandRm, aRoot, Arrays.asList(aRegion, aRoot));
                    PSRegion mergedRoot = PSRegion.fromWGRegion(commandWorld, commandRm.getRegion(aRoot.getId()));
                    boolean reopenMenu = mergedRoot != null && !getGUI(commandPlayer, mergedRoot).isEmpty();
                    return MergeResult.success(reopenMenu);
                } catch (WGMerge.RegionHoleException e) {
                    return MergeResult.hole();
                } catch (WGMerge.RegionCannotMergeWhileRentedException e) {
                    return MergeResult.rentedRejection(PSL.CANNOT_MERGE_RENTED_REGION.msg().replace("%region%", e.getRentedRegion().getName() == null ? e.getRentedRegion().getId() : e.getRentedRegion().getName()));
                }
            }).thenAccept(result -> {
                Runnable completion = () -> {
                    MergeResult mergeResult = (MergeResult) result;
                    PSL.msg(commandPlayer, mergeResult.message);
                    if (mergeResult.type == MergeResult.Type.SUCCESS && mergeResult.reopenMenu) {
                        FoliaScheduler.runGlobal(() -> Bukkit.dispatchCommand(commandPlayer, ProtectionStones.getInstance().getConfigOptions().base_command + " merge"));
                    }
                };
                FoliaScheduler.runEntity(commandPlayer, completion);
            });

        } else {
            PSL.msg(s, PSL.MERGE_HELP.msg());
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return null;
    }
}
