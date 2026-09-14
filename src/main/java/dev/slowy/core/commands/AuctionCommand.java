package dev.slowy.core.commands;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.auction.AuctionManager;
import dev.slowy.core.auction.AuctionText;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.*;

@NullMarked
public final class AuctionCommand implements SlowyBasicCommand {

    private final SlowyCore plugin;
    private final AuctionManager auctionManager;

    public AuctionCommand(SlowyCore plugin, AuctionManager auctionManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.auctionManager = Objects.requireNonNull(auctionManager, "auctionManager cannot be null");
    }

    @Override
    public String name() {
        return "auction";
    }

    @Override
    public String description() {
        return "Open the server auction house or list items for sale.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(AuctionText.MSG_PLAYER_ONLY));
            return;
        }

        if (args.length == 0) {
            auctionManager.openAuctionBrowser(player, 1);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell" -> {
                if (args.length < 2) {
                    auctionManager.openInsertItemGui(player);
                    return;
                }

                Double price = AuctionManager.parsePrice(args[1]);
                if (price == null) {
                    player.sendMessage(ColorUtils.parse("<red>Invalid price amount! Example: /auction sell 5000 or /auction sell 50k</red>"));
                    return;
                }

                ItemStack inHand = player.getInventory().getItemInMainHand();
                auctionManager.listItem(player, inHand, price);
            }

            case "setprice" -> {
                if (args.length < 2) {
                    auctionManager.openPriceInputDialog(player, "Please enter a price!");
                    return;
                }
                Double price = AuctionManager.parsePrice(args[1]);
                if (price == null) {
                    auctionManager.openPriceInputDialog(player, "Invalid price format! Example: 5000 or 50k");
                    return;
                }
                if (price < AuctionText.MIN_PRICE) {
                    auctionManager.openPriceInputDialog(player, "Minimum price is $" + plugin.getEconomyManager().formatNicest(AuctionText.MIN_PRICE));
                    return;
                }
                if (price > AuctionText.MAX_PRICE) {
                    auctionManager.openPriceInputDialog(player, "Maximum price is $" + plugin.getEconomyManager().formatNicest(AuctionText.MAX_PRICE));
                    return;
                }
                auctionManager.openConfirmListingGui(player, price);
            }

            case "cancelpending" -> {
                auctionManager.cancelPendingListing(player);
                auctionManager.openMyListings(player);
            }

            case "search" -> {
                if (args.length == 1) {
                    auctionManager.openSearchDialog(player);
                } else {
                    String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    auctionManager.handleSearchSubmit(player, query);
                }
            }

            case "dosearch" -> {
                String query = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
                auctionManager.handleSearchSubmit(player, query);
            }

            case "listings", "my", "mylistings" -> auctionManager.openMyListings(player);

            case "help" -> sendHelp(player);

            default -> auctionManager.openAuctionBrowser(player, 1);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtils.parse("<dark_gray><strikethrough>----------------------------------------</strikethrough></dark_gray>"));
        player.sendMessage(ColorUtils.parse("<#1DA1F2><b>ᴀᴜᴄᴛɪᴏɴ ʜᴏᴜꜱᴇ ɢᴜɪᴅᴇ</b></#1DA1F2>"));
        player.sendMessage(ColorUtils.parse("<dark_gray><strikethrough>----------------------------------------</strikethrough></dark_gray>"));
        player.sendMessage(ColorUtils.parse("<#FFE600>/auction</#FFE600> <dark_gray>-</dark_gray> <gray>Open Auction House browser</gray>"));
        player.sendMessage(ColorUtils.parse("<#FFE600>/auction sell <price></#FFE600> <dark_gray>-</dark_gray> <gray>List item in main hand for sale</gray>"));
        player.sendMessage(ColorUtils.parse("<#FFE600>/auction search [query]</#FFE600> <dark_gray>-</dark_gray> <gray>Search listings</gray>"));
        player.sendMessage(ColorUtils.parse("<#FFE600>/auction listings</#FFE600> <dark_gray>-</dark_gray> <gray>View & manage your active listings</gray>"));
        player.sendMessage(ColorUtils.parse("<dark_gray><strikethrough>----------------------------------------</strikethrough></dark_gray>"));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return List.of("sell", "search", "listings", "help").stream()
                    .filter(s -> s.startsWith(token))
                    .toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            String token = args[1].toLowerCase(Locale.ROOT);
            return List.of("1000", "5000", "10000", "50000").stream()
                    .filter(s -> s.startsWith(token))
                    .toList();
        }
        return Collections.emptyList();
    }
}
