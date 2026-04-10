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

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import dev.espi.protectionstones.*;
import dev.espi.protectionstones.compat.FoliaScheduler;
import dev.espi.protectionstones.utils.LimitUtil;
import dev.espi.protectionstones.utils.UUIDCache;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArgAddRemove implements PSCommandArg {

    @Override
    public List<String> getNames() {
        return Arrays.asList("add", "remove", "addowner", "removeowner");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.members", "protectionstones.owners");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        HashMap<String, Boolean> m = new HashMap<>();
        m.put("-a", false);
        return m;
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        Player p = (Player) s;
        String operationType = args[0].toLowerCase(); // add, remove, addowner, removeowner

        // check permission
        if ((operationType.equals("add") || operationType.equals("remove")) && !p.hasPermission("protectionstones.members")) {
            return PSL.msg(p, PSL.NO_PERMISSION_MEMBERS.msg());
        } else if ((operationType.equals("addowner") || operationType.equals("removeowner")) && !p.hasPermission("protectionstones.owners")) {
            return PSL.msg(p, PSL.NO_PERMISSION_OWNERS.msg());
        }

        // determine player to be added or removed
        if (args.length < 2) {
            return PSL.msg(p, PSL.COMMAND_REQUIRES_PLAYER_NAME.msg());
        }
        if (!UUIDCache.containsName(args[1])) {
            return PSL.msg(p, PSL.PLAYER_NOT_FOUND.msg());
        }

        // user being added
        UUID addPlayerUuid = UUIDCache.getUUIDFromName(args[1]);
        String addPlayerName = UUIDCache.getNameFromUUID(addPlayerUuid);

        // snapshot player state on command thread
        UUID commandPlayerUuid = p.getUniqueId();
        org.bukkit.World commandPlayerWorld = p.getWorld();
        org.bukkit.Location commandPlayerLocation = p.getLocation();

        // one-region branch: do immediate checks on command thread
        if (!flags.containsKey("-a")) {
            PSRegion r = PSRegion.fromLocationGroup(commandPlayerLocation);

            if (r == null) {
                return PSL.msg(p, PSL.NOT_IN_REGION.msg());
            } else if (WGUtils.hasNoAccess(r.getWGRegion(), p, WorldGuardPlugin.inst().wrapPlayer(p), false)) {
                return PSL.msg(p, PSL.NO_ACCESS.msg());
            } else if (operationType.equals("removeowner") && addPlayerUuid.equals(commandPlayerUuid) && r.getOwners().size() == 1) {
                return PSL.msg(p, PSL.CANNOT_REMOVE_YOURSELF_LAST_OWNER.msg());
            }

            final PSRegion regionToModify = r;

            FoliaScheduler.callGlobal(() -> {
                List<String> messages = new ArrayList<>();

                if (operationType.equals("addowner")) {
                    String err = determinePlayerSurpassedLimit(Collections.singletonList(regionToModify), PSPlayer.fromUUID(addPlayerUuid));
                    if (err != null) {
                        messages.add(err);
                        return messages;
                    }
                }

                switch (operationType) {
                    case "add":
                        regionToModify.addMember(addPlayerUuid);
                        messages.add(PSL.ADDED_TO_REGION.msg().replace("%player%", addPlayerName));
                        break;
                    case "remove":
                        regionToModify.removeMember(addPlayerUuid);
                        messages.add(PSL.REMOVED_FROM_REGION.msg().replace("%player%", addPlayerName));
                        break;
                    case "addowner":
                        regionToModify.addOwner(addPlayerUuid);
                        messages.add(PSL.ADDED_TO_REGION.msg().replace("%player%", addPlayerName));
                        break;
                    case "removeowner":
                        regionToModify.removeOwner(addPlayerUuid);
                        messages.add(PSL.REMOVED_FROM_REGION.msg().replace("%player%", addPlayerName));
                        break;
                }

                return messages;
            }).thenAccept(result -> FoliaScheduler.runEntity(p, () -> {
                @SuppressWarnings("unchecked")
                List<String> messages = (List<String>) result;
                for (String message : messages) {
                    PSL.msg(p, message);
                }
                if (operationType.equals("add") || operationType.equals("addowner")) {
                    FoliaScheduler.runAsync(() -> UUIDCache.storeWGProfile(addPlayerUuid, addPlayerName));
                }
            }));
            return true;
        }

        // -a branch: run in global phase
        FoliaScheduler.callGlobal(() -> {
            // don't let players remove themself from all of their regions
            if (operationType.equals("removeowner") && addPlayerUuid.equals(commandPlayerUuid)) {
                return Collections.singletonList(PSL.CANNOT_REMOVE_YOURSELF_FROM_ALL_REGIONS.msg());
            }

            List<PSRegion> regions = PSPlayer.fromUUID(commandPlayerUuid).getPSRegions(commandPlayerWorld, false);

            List<String> messages = new ArrayList<>();

            // check limit for addowner
            if (operationType.equals("addowner")) {
                String err = determinePlayerSurpassedLimit(regions, PSPlayer.fromUUID(addPlayerUuid));
                if (err != null) {
                    messages.add(err);
                    return messages;
                }
            }

            int count = 0;
            for (PSRegion r : regions) {
                if (operationType.equals("add") || operationType.equals("addowner")) {
                    count++;
                } else if ((operationType.equals("remove") && r.isMember(addPlayerUuid))
                        || (operationType.equals("removeowner") && r.isOwner(addPlayerUuid))) {
                    count++;
                }

                switch (operationType) {
                    case "add":
                        r.addMember(addPlayerUuid);
                        break;
                    case "remove":
                        r.removeMember(addPlayerUuid);
                        break;
                    case "addowner":
                        r.addOwner(addPlayerUuid);
                        break;
                    case "removeowner":
                        r.removeOwner(addPlayerUuid);
                        break;
                }
            }

            if (operationType.equals("add") || operationType.equals("addowner")) {
                messages.add(PSL.ADDED_TO_REGION.msg().replace("%player%", addPlayerName) + " (" + count + " regions)");
            } else {
                messages.add(PSL.REMOVED_FROM_REGION.msg().replace("%player%", addPlayerName) + " (" + count + " regions)");
            }

            return messages;
        }).thenAccept(result -> FoliaScheduler.runEntity(p, () -> {
            @SuppressWarnings("unchecked")
            List<String> messages = (List<String>) result;
            for (String message : messages) {
                PSL.msg(p, message);
            }
            if (operationType.equals("add") || operationType.equals("addowner")) {
                FoliaScheduler.runAsync(() -> UUIDCache.storeWGProfile(addPlayerUuid, addPlayerName));
            }
        }));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player p = (Player) sender;

        List<String> ret = new ArrayList<>();

        if (args.length == 2) {
            ret.add("-a");
        }

        try {
            if (args.length == 2 || (args.length == 3 && args[1].equals("-a"))) {

                switch (args[0].toLowerCase()) {
                    case "add":
                    case "addowner":
                        List<String> names = new ArrayList<>();
                        for (Player pAdd : Bukkit.getOnlinePlayers()) {
                            if (p.canSee(pAdd)) { // check if the player is not hidden
                                names.add(pAdd.getName());
                            }
                        }
                        ret.addAll(names);
                        break;
                    case "remove":
                    case "removeowner":
                        PSRegion r = PSRegion.fromLocationGroup(p.getLocation());
                        if (r != null) {
                            names = new ArrayList<>();
                            for (UUID uuid : args[0].equalsIgnoreCase("remove") ? r.getMembers() : r.getOwners()) {
                                names.add(UUIDCache.getNameFromUUID(uuid));
                            }
                            ret.addAll(names);
                        }
                        break;
                }

                try {
                    return StringUtil.copyPartialMatches(args[args.length - 1], ret, new ArrayList<>());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String determinePlayerSurpassedLimit(List<PSRegion> regionsToBeAddedTo, PSPlayer addedPlayer) {

        if (addedPlayer.getPlayer() == null && !ProtectionStones.getInstance().isLuckPermsSupportEnabled()) { // offline player
            if (ProtectionStones.getInstance().getConfigOptions().allowAddownerForOfflinePlayersWithoutLp) {
                // bypass config option
                return null;
            } else {
                // we need luckperms to determine region limits for offline players, so if luckperms isn't detected, prevent the action
                return PSL.ADDREMOVE_PLAYER_NEEDS_TO_BE_ONLINE.msg();
            }
        }

        // find total region amounts after player is added to the regions, and their existing total
        String err = LimitUtil.checkAddOwner(addedPlayer, regionsToBeAddedTo.stream()
                .flatMap(r -> {
                    if (r instanceof PSGroupRegion) {
                        return ((PSGroupRegion) r).getMergedRegions().stream();
                    }
                    return Stream.of(r);
                })
                .map(PSRegion::getTypeOptions)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        if (err.equals("")) {
            return null;
        } else {
            return err;
        }
    }
}
