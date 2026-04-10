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
import dev.espi.protectionstones.utils.MiscUtil;
import dev.espi.protectionstones.utils.TextGUI;
import dev.espi.protectionstones.utils.UUIDCache;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.EconomyResponse;
import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public class ArgTax implements PSCommandArg {

    private static final class TaxInfoPageResult {
        private final List<TextComponent> entries;
        private final boolean hasNextPage;
        private final int pageNum;

        private TaxInfoPageResult(List<TextComponent> entries, boolean hasNextPage, int pageNum) {
            this.entries = entries;
            this.hasNextPage = hasNextPage;
            this.pageNum = pageNum;
        }
    }

    private static final class TaxRegionInfoResult {
        private final boolean regionExists;
        private final String regionName;
        private final String taxRate;
        private final String taxPeriod;
        private final String taxPaymentPeriod;
        private final String taxAutopayer;
        private final String taxesOwed;

        private TaxRegionInfoResult(boolean regionExists, String regionName, String taxRate, String taxPeriod, String taxPaymentPeriod, String taxAutopayer, String taxesOwed) {
            this.regionExists = regionExists;
            this.regionName = regionName;
            this.taxRate = taxRate;
            this.taxPeriod = taxPeriod;
            this.taxPaymentPeriod = taxPaymentPeriod;
            this.taxAutopayer = taxAutopayer;
            this.taxesOwed = taxesOwed;
        }

        private static TaxRegionInfoResult notFound() {
            return new TaxRegionInfoResult(false, null, null, null, null, null, null);
        }

        private static TaxRegionInfoResult display(String regionName, String taxRate, String taxPeriod, String taxPaymentPeriod, String taxAutopayer, String taxesOwed) {
            return new TaxRegionInfoResult(true, regionName, taxRate, taxPeriod, taxPaymentPeriod, taxAutopayer, taxesOwed);
        }
    }

    private static final class TaxPayResult {
        private enum Type {
            REGION_NOT_FOUND,
            NOT_OWNER,
            NOT_ENOUGH_MONEY,
            DISPLAY
        }

        private final Type type;
        private final String amount;
        private final String regionName;

        private TaxPayResult(Type type, String amount, String regionName) {
            this.type = type;
            this.amount = amount;
            this.regionName = regionName;
        }

        private static TaxPayResult regionNotFound() {
            return new TaxPayResult(Type.REGION_NOT_FOUND, null, null);
        }

        private static TaxPayResult notOwner() {
            return new TaxPayResult(Type.NOT_OWNER, null, null);
        }

        private static TaxPayResult notEnoughMoney() {
            return new TaxPayResult(Type.NOT_ENOUGH_MONEY, null, null);
        }

        private static TaxPayResult display(String amount, String regionName) {
            return new TaxPayResult(Type.DISPLAY, amount, regionName);
        }
    }

    private static final class TaxAutoPayResult {
        private enum Type {
            REGION_NOT_FOUND,
            NOT_OWNER,
            SET_NO_AUTOPAYER,
            SET_AS_AUTOPAYER
        }

        private final Type type;
        private final String regionName;

        private TaxAutoPayResult(Type type, String regionName) {
            this.type = type;
            this.regionName = regionName;
        }

        private static TaxAutoPayResult regionNotFound() {
            return new TaxAutoPayResult(Type.REGION_NOT_FOUND, null);
        }

        private static TaxAutoPayResult notOwner() {
            return new TaxAutoPayResult(Type.NOT_OWNER, null);
        }

        private static TaxAutoPayResult setNoAutopayer(String regionName) {
            return new TaxAutoPayResult(Type.SET_NO_AUTOPAYER, regionName);
        }

        private static TaxAutoPayResult setAsAutopayer(String regionName) {
            return new TaxAutoPayResult(Type.SET_AS_AUTOPAYER, regionName);
        }
    }

    static final String INFO_HELP = ChatColor.AQUA + "> " + ChatColor.GRAY + "/ps tax info [region (optional)]", // maybe put in /ps info
            PAY_HELP = ChatColor.AQUA + "> " + ChatColor.GRAY + "/ps tax pay [amount] [region (optional)]",
            AUTOPAY_HELP = ChatColor.AQUA + "> " + ChatColor.GRAY + "/ps tax autopay [region (optional)]";

    @Override
    public List<String> getNames() {
        return Collections.singletonList("tax");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Collections.singletonList("protectionstones.tax");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        HashMap<String, Boolean> m = new HashMap<>();
        m.put("-p", true);
        return m;
    }

    private void runHelp(CommandSender s) {
        PSL.msg(s, PSL.TAX_HELP_HEADER.msg());
        PSL.msg(s, INFO_HELP);
        PSL.msg(s, PAY_HELP);
        PSL.msg(s, AUTOPAY_HELP);
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        if (!s.hasPermission("protectionstones.tax")) {
            return PSL.msg(s, PSL.NO_PERMISSION_TAX.msg());
        }
        if (!ProtectionStones.getInstance().getConfigOptions().taxEnabled) {
            return PSL.msg(s, ChatColor.RED + "Taxes are disabled! Enable it in the config.");
        }

        Player p = (Player) s;
        UUID snapshotUuid = p.getUniqueId();
        World snapshotWorld = p.getWorld();
        Location snapshotLocation = p.getLocation();

        if (args.length == 1 || args[1].equals("help")) {
            runHelp(s);
            return true;
        }

        switch (args[1]) {
            case "info":
                return taxInfo(args, flags, snapshotUuid, snapshotWorld, snapshotLocation, p);
            case "pay":
                return taxPay(args, snapshotUuid, snapshotWorld, snapshotLocation, p);
            case "autopay":
                return taxAutoPay(args, snapshotUuid, snapshotWorld, snapshotLocation, p);
            default:
                runHelp(s);
                break;
        }

        return true;
    }

    private static final int GUI_SIZE = 17;

    public boolean taxInfo(String[] args, HashMap<String, String> flags, UUID snapshotUuid, World snapshotWorld, Location snapshotLocation, Player player) {
        
        if (args.length == 2) { // /ps tax info
            final int pageNum = (flags.get("-p") == null || !MiscUtil.isValidInteger(flags.get("-p")) ? 0 : Integer.parseInt(flags.get("-p"))-1);
            
            FoliaScheduler.callGlobal(() -> {
                PSPlayer snapshotPlayer = PSPlayer.fromUUID(snapshotUuid);
                List<TextComponent> entries = new ArrayList<>();
                for (PSRegion r : snapshotPlayer.getTaxEligibleRegions()) {
                    double amountDue = 0;
                    for (PSRegion.TaxPayment tp : r.getTaxPaymentsDue()) {
                        amountDue += tp.getAmount();
                    }

                    TextComponent component;
                    if (r.getTaxAutopayer() != null && r.getTaxAutopayer().equals(snapshotPlayer.getUuid())) {
                        component = new TextComponent(PSL.TAX_PLAYER_REGION_INFO_AUTOPAYER.msg()
                                .replace("%region%", (r.getName() == null ? r.getId() : r.getName() + " (" + r.getId() + ")"))
                                .replace("%money%", String.format("%.2f", amountDue)));
                    } else {
                        component = new TextComponent(PSL.TAX_PLAYER_REGION_INFO.msg()
                                .replace("%region%", (r.getName() == null ? r.getId() : r.getName() + " (" + r.getId() + ")"))
                                .replace("%money%", String.format("%.2f", amountDue)));
                    }
                    component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " tax info " + r.getId()));
                    component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(PSL.TAX_CLICK_TO_SHOW_MORE_INFO.msg()).create()));
                    entries.add(component);
                }
                
                boolean hasNextPage = pageNum * GUI_SIZE + GUI_SIZE < entries.size();
                return new TaxInfoPageResult(entries, hasNextPage, pageNum);
            }).thenAccept(result -> {
                FoliaScheduler.runEntity(player, () -> {
                    TaxInfoPageResult pageResult = (TaxInfoPageResult) result;
                    
                    TextGUI.displayGUI(player, PSL.TAX_INFO_HEADER.msg(), "/" + ProtectionStones.getInstance().getConfigOptions().base_command + " tax info -p %page%", pageResult.pageNum, GUI_SIZE, pageResult.entries, true);

                    if (pageResult.hasNextPage)
                        PSL.msg(player, PSL.TAX_NEXT.msg().replace("%page%", pageResult.pageNum + 2 + ""));
                });
            });
        } else if (args.length == 3) { // /ps tax info [region]
            FoliaScheduler.callGlobal(() -> {
                PSPlayer.fromUUID(snapshotUuid);
                List<PSRegion> list = ProtectionStones.getPSRegions(snapshotWorld, args[2]);
                if (list.isEmpty()) {
                    return TaxRegionInfoResult.notFound();
                }
                PSRegion r = list.get(0);
                double taxesOwed = 0;
                for (PSRegion.TaxPayment tp : r.getTaxPaymentsDue()) {
                    taxesOwed += tp.getAmount();
                }
                
                String regionName = r.getName() == null ? r.getId() : r.getName() + " (" + r.getId() + ")";
                return TaxRegionInfoResult.display(
                        regionName,
                        String.format("%.2f", r.getTaxRate()),
                        r.getTaxPeriod(),
                        r.getTaxPaymentPeriod(),
                        r.getTaxAutopayer() == null ? "none" : UUIDCache.getNameFromUUID(r.getTaxAutopayer()),
                        String.format("%.2f", taxesOwed)
                );
            }).thenAccept(result -> {
                FoliaScheduler.runEntity(player, () -> {
                    TaxRegionInfoResult regionResult = (TaxRegionInfoResult) result;
                    if (!regionResult.regionExists) {
                        PSL.msg(player, PSL.REGION_DOES_NOT_EXIST.msg());
                    } else {
                        PSL.msg(player, PSL.TAX_REGION_INFO_HEADER.msg().replace("%region%", regionResult.regionName));
                        PSL.msg(player, PSL.TAX_REGION_INFO.msg()
                                    .replace("%taxrate%", regionResult.taxRate)
                                    .replace("%taxperiod%", regionResult.taxPeriod)
                                    .replace("%taxpaymentperiod%", regionResult.taxPaymentPeriod)
                                    .replace("%taxautopayer%", regionResult.taxAutopayer)
                                    .replace("%taxowed%", regionResult.taxesOwed));
                    }
                });
            });
        } else {
            FoliaScheduler.runEntity(player, () -> PSL.msg(player, INFO_HELP));
        }
        return true;
    }

    public boolean taxPay(String[] args, UUID snapshotUuid, World snapshotWorld, Location snapshotLocation, Player player) {
        if (args.length != 3 && args.length != 4)
            return PSL.msg(player, PAY_HELP);
        if (!NumberUtils.isNumber(args[2]))
            return PSL.msg(player, PAY_HELP);

        double payment = Double.parseDouble(args[2]);
        if (payment <= 0)
            return PSL.msg(player, PAY_HELP);

        final String regionArg = args.length == 4 ? args[3] : null;
        
        if (!PSPlayer.fromUUID(snapshotUuid).hasAmount(payment))
            return PSL.msg(player, PSL.NOT_ENOUGH_MONEY.msg().replace("%price%", String.format("%.2f", payment)));

        FoliaScheduler.callGlobal(() -> {
            PSPlayer snapshotPlayer = PSPlayer.fromUUID(snapshotUuid);
            PSRegion r = resolveRegion(regionArg, snapshotWorld, snapshotLocation);
            if (r == null) {
                return TaxPayResult.regionNotFound();
            }
            
            if (!r.isOwner(snapshotPlayer.getUuid())) {
                return TaxPayResult.notOwner();
            }
            
            if (!snapshotPlayer.hasAmount(payment)) {
                return TaxPayResult.notEnoughMoney();
            }
            
            EconomyResponse res = r.payTax(snapshotPlayer, payment);
            
            String regionName = r.getName() == null ? r.getId() : r.getName() + "(" + r.getId() + ")";
            return TaxPayResult.display(String.format("%.2f", res.amount), regionName);
        }).thenAccept(result -> {
            FoliaScheduler.runEntity(player, () -> {
                TaxPayResult payResult = (TaxPayResult) result;
                if (payResult.type == TaxPayResult.Type.REGION_NOT_FOUND) {
                    PSL.msg(player, PSL.NOT_IN_REGION.msg());
                } else if (payResult.type == TaxPayResult.Type.NOT_OWNER) {
                    PSL.msg(player, PSL.NOT_OWNER.msg());
                } else if (payResult.type == TaxPayResult.Type.NOT_ENOUGH_MONEY) {
                    PSL.msg(player, PSL.NOT_ENOUGH_MONEY.msg().replace("%price%", String.format("%.2f", payment)));
                } else if (payResult.type == TaxPayResult.Type.DISPLAY) {
                    PSL.msg(player, PSL.TAX_PAID.msg()
                            .replace("%amount%", payResult.amount)
                            .replace("%region%", payResult.regionName));
                }
            });
        });
        return true;
    }

    public boolean taxAutoPay(String[] args, UUID snapshotUuid, World snapshotWorld, Location snapshotLocation, Player player) {
        if (args.length != 2 && args.length != 3)
            return PSL.msg(player, AUTOPAY_HELP);

        final String regionArg = args.length == 3 ? args[2] : null;

        FoliaScheduler.callGlobal(() -> {
            PSPlayer snapshotPlayer = PSPlayer.fromUUID(snapshotUuid);
            PSRegion r = resolveRegion(regionArg, snapshotWorld, snapshotLocation);
            if (r == null) {
                return TaxAutoPayResult.regionNotFound();
            }
            
            if (!r.isOwner(snapshotPlayer.getUuid())) {
                return TaxAutoPayResult.notOwner();
            }
            
            String regionName = r.getName() == null ? r.getId() : r.getName() + "(" + r.getId() + ")";
            boolean isRemoving = r.getTaxAutopayer() != null && r.getTaxAutopayer().equals(snapshotPlayer.getUuid());
            
            if (isRemoving) {
                r.setTaxAutopayer(null);
                return TaxAutoPayResult.setNoAutopayer(regionName);
            } else {
                r.setTaxAutopayer(snapshotPlayer.getUuid());
                return TaxAutoPayResult.setAsAutopayer(regionName);
            }
        }).thenAccept(result -> {
            FoliaScheduler.runEntity(player, () -> {
                TaxAutoPayResult autoPayResult = (TaxAutoPayResult) result;
                if (autoPayResult.type == TaxAutoPayResult.Type.REGION_NOT_FOUND) {
                    PSL.msg(player, PSL.NOT_IN_REGION.msg());
                } else if (autoPayResult.type == TaxAutoPayResult.Type.NOT_OWNER) {
                    PSL.msg(player, PSL.NOT_OWNER.msg());
                } else if (autoPayResult.type == TaxAutoPayResult.Type.SET_NO_AUTOPAYER) {
                    PSL.msg(player, PSL.TAX_SET_NO_AUTOPAYER.msg().replace("%region%", autoPayResult.regionName));
                } else if (autoPayResult.type == TaxAutoPayResult.Type.SET_AS_AUTOPAYER) {
                    PSL.msg(player, PSL.TAX_SET_AS_AUTOPAYER.msg().replace("%region%", autoPayResult.regionName));
                }
            });
        });
        return true;
    }

    public PSRegion resolveRegion(String region, World world, Location location) {
        PSRegion r;
        if (region == null) {
            r = PSRegion.fromLocationGroup(location);
            if (r == null) {
                return null;
            }

            if (r.getTypeOptions() == null || r.getTypeOptions().taxPeriod == -1) {
                return null;
            }

        } else {
            List<PSRegion> list = ProtectionStones.getPSRegions(world, region);
            if (list.isEmpty()) {
                return null;
            } else {
                r = list.get(0);
            }
        }
        return r;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 2) {
            List<String> arg = Arrays.asList("info", "pay", "autopay");
            return StringUtil.copyPartialMatches(args[1], arg, new ArrayList<>());
        }
        return null;
    }
}
