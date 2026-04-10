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

import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSPlayer;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.compat.FoliaScheduler;
import dev.espi.protectionstones.utils.UUIDCache;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

public class ArgList implements PSCommandArg {

    private static final class ListResult {
        private enum Type {
            HELP,
            NO_REGIONS,
            NO_REGIONS_PLAYER,
            DISPLAY
        }

        private final Type type;
        private final String playerName;
        private final String header;
        private final String ownerHeader;
        private final String memberHeader;
        private final List<String> ownerOf;
        private final List<String> memberOf;

        private ListResult(Type type, String playerName, String header, String ownerHeader, String memberHeader, List<String> ownerOf, List<String> memberOf) {
            this.type = type;
            this.playerName = playerName;
            this.header = header;
            this.ownerHeader = ownerHeader;
            this.memberHeader = memberHeader;
            this.ownerOf = ownerOf;
            this.memberOf = memberOf;
        }

        private static ListResult help() {
            return new ListResult(Type.HELP, null, null, null, null, null, null);
        }

        private static ListResult noRegions() {
            return new ListResult(Type.NO_REGIONS, null, null, null, null, null, null);
        }

        private static ListResult noRegionsPlayer(String playerName) {
            return new ListResult(Type.NO_REGIONS_PLAYER, playerName, null, null, null, null, null);
        }

        private static ListResult display(String header, String ownerHeader, String memberHeader, List<String> ownerOf, List<String> memberOf) {
            return new ListResult(Type.DISPLAY, null, header, ownerHeader, memberHeader, ownerOf, memberOf);
        }
    }
    @Override
    public List<String> getNames() {
        return Collections.singletonList("list");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.list");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        if (!s.hasPermission("protectionstones.list"))
            return PSL.msg(s, PSL.NO_PERMISSION_LIST.msg());

        if (args.length == 2 && !s.hasPermission("protectionstones.list.others"))
            return PSL.msg(s, PSL.NO_PERMISSION_LIST_OTHERS.msg());

        if (args.length == 2 && !UUIDCache.containsName(args[1]))
            return PSL.msg(s, PSL.PLAYER_NOT_FOUND.msg());

        CommandSender commandSender = s;
        Player p = (Player) s;
        final World playerWorld = p.getWorld();
        final UUID playerUuid = p.getUniqueId();
        final boolean isCurrentPlayer = args.length == 1;
        
        FoliaScheduler.callGlobal(() -> {
            List<PSRegion> regions;
            UUID targetUuid;
            
            if (args.length == 1) {
                targetUuid = playerUuid;
                regions = PSPlayer.fromUUID(playerUuid).getPSRegionsCrossWorld(playerWorld, true);
            } else if (args.length == 2) {
                targetUuid = UUIDCache.getUUIDFromName(args[1]);
                regions = PSPlayer.fromUUID(targetUuid).getPSRegionsCrossWorld(playerWorld, true);
            } else {
                return ListResult.help();
            }
            
            List<String> ownerOf = new ArrayList<>(), memberOf = new ArrayList<>();
            for (PSRegion r : regions) {
                if (r.isOwner(targetUuid)) {
                    if (r.getName() == null) {
                        ownerOf.add(ChatColor.GRAY + "> " + ChatColor.AQUA + r.getId());
                    } else {
                        ownerOf.add(ChatColor.GRAY + "> " + ChatColor.AQUA + r.getName() + " (" + r.getId() + ")");
                    }
                }
                if (r.isMember(targetUuid)) {
                    if (r.getName() == null) {
                        memberOf.add(ChatColor.GRAY + "> " + ChatColor.AQUA + r.getId());
                    } else {
                        memberOf.add(ChatColor.GRAY + "> " + ChatColor.AQUA + r.getName() + " (" + r.getId() + ")");
                    }
                }
            }
            
            if (ownerOf.isEmpty() && memberOf.isEmpty()) {
                if (isCurrentPlayer) {
                    return ListResult.noRegions();
                } else {
                    return ListResult.noRegionsPlayer(UUIDCache.getNameFromUUID(targetUuid));
                }
            }
            
            String header = PSL.LIST_HEADER.msg().replace("%player%", UUIDCache.getNameFromUUID(targetUuid));
            String ownerHeader = !ownerOf.isEmpty() ? PSL.LIST_OWNER.msg() : null;
            String memberHeader = !memberOf.isEmpty() ? PSL.LIST_MEMBER.msg() : null;
            
            return ListResult.display(header, ownerHeader, memberHeader, ownerOf, memberOf);
        }).thenAccept(result -> {
            Runnable completion = () -> {
                ListResult listResult = (ListResult) result;
                if (listResult.type == ListResult.Type.HELP) {
                    PSL.msg(commandSender, PSL.LIST_HELP.msg());
                } else if (listResult.type == ListResult.Type.NO_REGIONS) {
                    PSL.msg(commandSender, PSL.LIST_NO_REGIONS.msg());
                } else if (listResult.type == ListResult.Type.NO_REGIONS_PLAYER) {
                    PSL.msg(commandSender, PSL.LIST_NO_REGIONS_PLAYER.msg().replace("%player%", listResult.playerName));
                } else if (listResult.type == ListResult.Type.DISPLAY) {
                    PSL.msg(commandSender, listResult.header);
                    if (listResult.ownerHeader != null) {
                        PSL.msg(commandSender, listResult.ownerHeader);
                        for (String str : listResult.ownerOf) commandSender.sendMessage(str);
                    }
                    if (listResult.memberHeader != null) {
                        PSL.msg(commandSender, listResult.memberHeader);
                        for (String str : listResult.memberOf) commandSender.sendMessage(str);
                    }
                }
            };
            if (commandSender instanceof Player) {
                FoliaScheduler.runEntity(p, completion);
            } else {
                FoliaScheduler.runGlobal(completion);
            }
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!sender.hasPermission("protectionstones.list") || !sender.hasPermission("protectionstones.list.others")) {
            return null;
        }
        if (args.length == 2) {
            // autocomplete with online player list
            return StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(HumanEntity::getName).collect(Collectors.toList()), new ArrayList<>());
        }

        return null;
    }

    private void display(CommandSender s, List<PSRegion> regions, UUID pUUID, boolean isCurrentPlayer) {
        List<String> ownerOf = new ArrayList<>(), memberOf = new ArrayList<>();
        for (PSRegion r : regions) {
            if (r.isOwner(pUUID)) {
                if (r.getName() == null) {
                    ownerOf.add(ChatColor.GRAY + "> " + ChatColor.AQUA + r.getId());
                } else {
                    ownerOf.add(ChatColor.GRAY + "> " + ChatColor.AQUA + r.getName() + " (" + r.getId() + ")");
                }
            }
            if (r.isMember(pUUID)) {
                if (r.getName() == null) {
                    memberOf.add(ChatColor.GRAY + "> " + ChatColor.AQUA + r.getId());
                } else {
                    memberOf.add(ChatColor.GRAY + "> " + ChatColor.AQUA + r.getName() + " (" + r.getId() + ")");
                }
            }
        }

        if (ownerOf.isEmpty() && memberOf.isEmpty()) {
            if (isCurrentPlayer) {
                PSL.msg(s, PSL.LIST_NO_REGIONS.msg());
            } else {
                PSL.msg(s, PSL.LIST_NO_REGIONS_PLAYER.msg().replace("%player%", UUIDCache.getNameFromUUID(pUUID)));
            }
            return;
        }

        PSL.msg(s, PSL.LIST_HEADER.msg().replace("%player%", UUIDCache.getNameFromUUID(pUUID)));

        if (!ownerOf.isEmpty()) {
            PSL.msg(s, PSL.LIST_OWNER.msg());
            for (String str : ownerOf) s.sendMessage(str);
        }
        if (!memberOf.isEmpty()) {
            PSL.msg(s, PSL.LIST_MEMBER.msg());
            for (String str : memberOf) s.sendMessage(str);
        }
    }

}
