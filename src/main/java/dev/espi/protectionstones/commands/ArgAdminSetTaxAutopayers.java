/*
 * Copyright 2019 ProtectionStones team and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package dev.espi.protectionstones.commands;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.compat.FoliaScheduler;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicInteger;

class ArgAdminSetTaxAutopayers {

    private static final class TaxAutopayersResult {
        private final int processedCount;
        private final String message;

        private TaxAutopayersResult(int processedCount, String message) {
            this.processedCount = processedCount;
            this.message = message;
        }
    }

    static boolean argumentAdminSetTaxAutopayers(CommandSender s, String[] args) {
        if (!ProtectionStones.getInstance().getConfigOptions().taxEnabled) {
            return PSL.msg(s, ChatColor.RED + "Taxes are disabled! Enable it in the config.");
        }

        PSL.msg(s, ChatColor.GRAY + "Scanning through regions, and setting tax autopayers for regions that don't have one...");

        final CommandSender commandSender = s;
        final boolean playerSender = commandSender instanceof Player;
        FoliaScheduler.callGlobal(() -> {
            final AtomicInteger processedCount = new AtomicInteger();
            WGUtils.getAllRegionManagers().forEach((w, rgm) -> {
                for (ProtectedRegion r : rgm.getRegions().values()) {
                    PSRegion psr = PSRegion.fromWGRegion(w, r);

                    if (psr != null && psr.getTypeOptions() != null && psr.getTypeOptions().taxPeriod != -1 && psr.getTaxAutopayer() == null) {
                        if (psr.getOwners().size() >= 1) {
                            psr.setTaxAutopayer(psr.getOwners().get(0));
                            processedCount.incrementAndGet();
                        }
                    }

                }
            });
            int processed = processedCount.get();
            return new TaxAutopayersResult(processed, ChatColor.GREEN + "Complete! Processed " + processed + " regions.");
        }).thenAccept(result -> {
            Runnable completion = () -> {
                TaxAutopayersResult taxResult = (TaxAutopayersResult) result;
                PSL.msg(commandSender, taxResult.message);
            };
            if (playerSender) {
                FoliaScheduler.runEntity((Player) commandSender, completion);
            } else {
                FoliaScheduler.runGlobal(completion);
            }
        });

        return true;
    }
}
