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
import dev.espi.protectionstones.utils.ChatUtil;
import dev.espi.protectionstones.utils.MiscUtil;
import dev.espi.protectionstones.utils.TextGUI;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ArgHome implements PSCommandArg {

    private static ConcurrentHashMap<UUID, List<String>> tabCache = new ConcurrentHashMap<>();

    @Override
    public List<String> getNames() {
        return Collections.singletonList("home");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.home");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        HashMap<String, Boolean> h = new HashMap<>();
        h.put("-p", true);
        return h;
    }

    // tab completion
    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player p)) return null;

        if (args.length == 2) {
            UUID snapshotUuid = p.getUniqueId();
            org.bukkit.World snapshotWorld = p.getWorld();

            if (tabCache.get(snapshotUuid) == null) {
                FoliaScheduler.callGlobal(() -> {
                    PSPlayer psp = PSPlayer.fromUUID(snapshotUuid);
                    List<PSRegion> regions = psp.getHomes(snapshotWorld);
                    List<String> regionNames = new ArrayList<>();
                    for (PSRegion r : regions) {
                        if (r.getName() != null) {
                            regionNames.add(r.getName());
                        } else {
                            regionNames.add(r.getId());
                        }
                    }
                    return Collections.unmodifiableList(regionNames);
                }).thenAccept(result -> {
                    @SuppressWarnings("unchecked")
                    List<String> immutableNames = (List<String>) result;
                    tabCache.put(snapshotUuid, immutableNames);
                    FoliaScheduler.runAsyncLater(() -> tabCache.remove(snapshotUuid), 200L);
                });
                return new ArrayList<>();
            }

            List<String> cached = tabCache.get(snapshotUuid);
            if (cached != null) {
                return StringUtil.copyPartialMatches(args[1], cached, new ArrayList<>());
            }
            return new ArrayList<>();
        }
        return null;
    }

    private static final int GUI_SIZE = 17;

    private void openHomeGUI(PSPlayer psp, List<PSRegion> homes, int page) {
        List<TextComponent> entries = new ArrayList<>();
        for (PSRegion r : homes) {
            String msg;
            if (r.getName() == null) {
                msg = ChatColor.GRAY + "> " + ChatColor.AQUA + r.getId();
            } else {
                msg = ChatColor.GRAY + "> " + ChatColor.AQUA + r.getName() + " (" + r.getId() + ")";
            }
            TextComponent tc = new TextComponent(msg);
            tc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(PSL.HOME_CLICK_TO_TP.msg()).create()));
            if (r.getName() == null) {
                tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " home " + r.getId()));
            } else {
                tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " home " + r.getName()));
            }
            entries.add(tc);
        }

        TextGUI.displayGUI(psp.getPlayer(), PSL.HOME_HEADER.msg(), "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " home -p %page%", page, GUI_SIZE, entries, true);

        if (page * GUI_SIZE + GUI_SIZE < entries.size())
            PSL.msg(psp, PSL.HOME_NEXT.msg().replace("%page%", page + 2 + ""));
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        Player p = (Player) s;

        // prelim checks
        if (!p.hasPermission("protectionstones.home"))
            return PSL.msg(p, PSL.NO_PERMISSION_HOME.msg());

        if (args.length != 2 && args.length != 1)
            return PSL.msg(p, PSL.HOME_HELP.msg());

        UUID snapshotUuid = p.getUniqueId();
        org.bukkit.World snapshotWorld = p.getWorld();
        String snapshotQuery = args.length == 2 ? args[1] : null;
        int snapshotPage = flags.get("-p") == null || !MiscUtil.isValidInteger(flags.get("-p")) ? 0 : Integer.parseInt(flags.get("-p")) - 1;

        FoliaScheduler.callGlobal(() -> {
            PSPlayer psp = PSPlayer.fromUUID(snapshotUuid);
            if (snapshotQuery == null) {
                List<PSRegion> regions = psp.getHomes(snapshotWorld);
                if (regions.size() == 1) {
                    return new Object[] { "SINGLE_HOME", regions.get(0) };
                } else if (regions.isEmpty()) {
                    return new Object[] { "NO_REGIONS" };
                } else {
                    return new Object[] { "MULTI_HOME", psp, regions, snapshotPage };
                }
            } else {
                List<PSRegion> regions = psp.getHomes(snapshotWorld)
                        .stream()
                        .filter(region -> region.getId().equals(snapshotQuery)
                                || (region.getName() != null && region.getName().equals(snapshotQuery)))
                        .collect(Collectors.toList());

                if (regions.isEmpty()) {
                    return new Object[] { "NOT_FOUND" };
                } else if (regions.size() > 1) {
                    return new Object[] { "DUPLICATE_ALIAS", regions };
                } else {
                    return new Object[] { "SINGLE_HOME", regions.get(0) };
                }
            }
        }).thenAccept(result -> {
            FoliaScheduler.runEntity(p, () -> {
                Object[] data = (Object[]) result;
                String type = (String) data[0];
                if ("SINGLE_HOME".equals(type)) {
                    PSRegion r = (PSRegion) data[1];
                    ArgTp.teleportPlayer(p, r);
                } else if ("MULTI_HOME".equals(type)) {
                    @SuppressWarnings("unchecked")
                    List<PSRegion> regions = (List<PSRegion>) data[2];
                    int page = (int) data[3];
                    openHomeGUI((PSPlayer) data[1], regions, page);
                } else if ("NOT_FOUND".equals(type)) {
                    PSL.msg(p, PSL.REGION_DOES_NOT_EXIST.msg());
                } else if ("DUPLICATE_ALIAS".equals(type)) {
                    @SuppressWarnings("unchecked")
                    List<PSRegion> regions = (List<PSRegion>) data[1];
                    ChatUtil.displayDuplicateRegionAliases(p, regions);
                } else if ("NO_REGIONS".equals(type)) {
                    PSL.msg(p, PSL.REGION_DOES_NOT_EXIST.msg());
                }
            });
        });

        return true;
    }
}
