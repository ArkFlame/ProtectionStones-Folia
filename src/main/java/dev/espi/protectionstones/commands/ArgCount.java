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

import dev.espi.protectionstones.PSGroupRegion;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSPlayer;
import dev.espi.protectionstones.compat.FoliaScheduler;
import dev.espi.protectionstones.utils.UUIDCache;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class ArgCount implements PSCommandArg {

    private static final class CountResult {
        private enum Type {
            ERROR,
            DISPLAY
        }

        private final Type type;
        private final String messageKey;
        private final String firstMessage;
        private final String secondMessage;

        private CountResult(Type type, String messageKey, String firstMessage, String secondMessage) {
            this.type = type;
            this.messageKey = messageKey;
            this.firstMessage = firstMessage;
            this.secondMessage = secondMessage;
        }

        private static CountResult error(String messageKey) {
            return new CountResult(Type.ERROR, messageKey, null, null);
        }

        private static CountResult display(String firstMessage, String secondMessage) {
            return new CountResult(Type.DISPLAY, null, firstMessage, secondMessage);
        }
    }

    // Only PS regions, not other regions
    static int[] countRegionsOfPlayer(UUID uuid, World w) {
        int[] count = {0, 0}; // total, including merged

        PSPlayer psp = PSPlayer.fromUUID(uuid);
        psp.getPSRegions(w, false).forEach(r -> {
            count[0]++;
            if (r instanceof PSGroupRegion) {
                count[1] += ((PSGroupRegion) r).getMergedRegions().size();
            }
        });

        return count;
    }

    @Override
    public List<String> getNames() {
        return Collections.singletonList("count");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.count", "protectionstones.count.others");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    // /ps count
    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        CommandSender commandSender = s;
        Player p = (Player) s;
        final World playerWorld = p.getWorld();
        final UUID playerUuid = p.getUniqueId();
        final boolean hasCountPerm = p.hasPermission("protectionstones.count");
        final boolean hasCountOthersPerm = p.hasPermission("protectionstones.count.others");
        
        FoliaScheduler.callGlobal(() -> {
            if (args.length == 1) {
                if (!hasCountPerm) {
                    return CountResult.error("NO_PERMISSION_COUNT");
                }

                int[] count = countRegionsOfPlayer(playerUuid, playerWorld);
                String msg1 = PSL.PERSONAL_REGION_COUNT.msg().replace("%num%", "" + count[0]);
                String msg2 = count[1] != 0 ? PSL.PERSONAL_REGION_COUNT_MERGED.msg().replace("%num%", ""+count[1]) : null;
                return CountResult.display(msg1, msg2);

            } else if (args.length == 2) {

                if (!hasCountOthersPerm) {
                    return CountResult.error("NO_PERMISSION_COUNT_OTHERS");
                }
                if (!UUIDCache.containsName(args[1])) {
                    return CountResult.error("PLAYER_NOT_FOUND");
                }

                UUID countUuid = UUIDCache.getUUIDFromName(args[1]);
                int[] count = countRegionsOfPlayer(countUuid, playerWorld);
                String countName = UUIDCache.getNameFromUUID(countUuid);
                
                String msg1 = PSL.OTHER_REGION_COUNT.msg()
                        .replace("%player%", countName)
                        .replace("%num%", "" + count[0]);
                String msg2 = count[1] != 0 ? PSL.OTHER_REGION_COUNT_MERGED.msg()
                        .replace("%player%", countName)
                        .replace("%num%", "" + count[1]) : null;
                return CountResult.display(msg1, msg2);
            } else {
                return CountResult.error("COUNT_HELP");
            }
        }).thenAccept(result -> {
            Runnable completion = () -> {
                CountResult countResult = (CountResult) result;
                if (countResult.type == CountResult.Type.ERROR) {
                    switch (countResult.messageKey) {
                        case "NO_PERMISSION_COUNT":
                            PSL.msg(commandSender, PSL.NO_PERMISSION_COUNT.msg());
                            break;
                        case "NO_PERMISSION_COUNT_OTHERS":
                            PSL.msg(commandSender, PSL.NO_PERMISSION_COUNT_OTHERS.msg());
                            break;
                        case "PLAYER_NOT_FOUND":
                            PSL.msg(commandSender, PSL.PLAYER_NOT_FOUND.msg());
                            break;
                        case "COUNT_HELP":
                            PSL.msg(commandSender, PSL.COUNT_HELP.msg());
                            break;
                    }
                } else {
                    PSL.msg(commandSender, countResult.firstMessage);
                    if (countResult.secondMessage != null) {
                        PSL.msg(commandSender, countResult.secondMessage);
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
        return null;
    }

}
