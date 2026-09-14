package dev.slowy.core.auction;

import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@NullMarked
public final class AuctionText {

    private AuctionText() {}

    // In-code configuration matching Slowy Lama
    public static final int MAX_ACTIVE_LISTINGS = 17;
    public static final double MIN_PRICE = 100.0;
    public static final double MAX_PRICE = 1_000_000_000.0;
    public static final double LISTING_FEE = 0.0;
    public static final double TAX_PERCENT = 0.0;
    public static final Set<Material> BLOCKED_MATERIALS = Set.of(
            Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID,
            Material.JIGSAW, Material.LIGHT
    );

    // GUI Titles
    public static final String TITLE_BROWSER = "ᴀᴜᴄᴛɪᴏɴ [Page %d] ";
    public static final String TITLE_YOUR_ITEMS = "ʏᴏᴜʀ ɪᴛᴇᴍꜱ";
    public static final String TITLE_INSERT_ITEM = "ɪɴꜱᴇʀᴛ ɪᴛᴇᴍ";
    public static final String TITLE_PRICE_DIALOG = "🏷 Set Selling Price";
    public static final String TITLE_CONFIRM_LISTING = "ᴄᴏɴꜰɪʀᴍ ʟɪꜱᴛɪɴɢ";
    public static final String TITLE_BUY_CONFIRM = "ᴄᴏɴꜰɪʀᴍ ᴘᴜʀᴄʜᴀꜱᴇ";
    public static final String TITLE_SHULKER_PREVIEW = "&8Shulker Box (%s)";
    public static final String TITLE_SEARCH_DIALOG = "Auction Search";

    // Prefixes & Navigation Buttons
    public static final String PREFIX_OPTION_ACTIVE = "&#39FF14▪ ";
    public static final String PREFIX_OPTION_INACTIVE = "&7▪ ";
    public static final String BTN_PREVIOUS = "&#1DA1F2ᴘʀᴇᴠɪᴏᴜꜱ";
    public static final String BTN_NEXT = "&#1DA1F2ɴᴇxᴛ";
    public static final String BTN_BACK = "&#FF0055ʙᴀᴄᴋ";

    // Browser & Menu Buttons
    public static final String BTN_SORT = "&#1DA1F2ꜱᴏʀᴛ";
    public static final String BTN_FILTER = "&#1DA1F2ꜰɪʟᴛᴇʀ";
    public static final String BTN_REFRESH = "&#1DA1F2ᴀᴜᴄᴛɪᴏɴ";
    public static final List<String> LORE_REFRESH = List.of("&7Click to refresh");

    public static final String BTN_SEARCH = "&#1DA1F2ꜱᴇᴀʀᴄʜ";
    public static final List<String> LORE_SEARCH = List.of("&7Click to search");
    public static final String LORE_SEARCH_ACTIVE = "&7Active: &#FFE600\"%s\"";
    public static final String LORE_SEARCH_CLEAR_HINT = "&8Right-click to clear filter";

    public static final String BTN_YOUR_ITEMS = "&#1DA1F2ʏᴏᴜʀ ɪᴛᴇᴍꜱ";
    public static final String BTN_SELL_ITEM = "&#39FF14ꜱᴇʟʟ ɪᴛᴇᴍ";
    public static final String BTN_CONFIRM = "&#39FF14ᴄᴏɴꜰɪʀᴍ";
    public static final String DESC_CONFIRM_LISTING_ITEM = "&7Sell for &#39FF14%s%s";
    public static final String BTN_CANCEL = "&#FF0055ᴄᴀɴᴄᴇʟ";
    public static final String BTN_BUY_CONFIRM = "&#39FF14ᴄᴏɴꜰɪʀᴍ";
    public static final List<String> LORE_BUY_CONFIRM = List.of("&7Click to purchase");
    public static final List<String> LORE_BUY_CANCEL = List.of("&7Click to return to auction");
    public static final String BTN_SHULKER_CLOSE = "&#FF00BD« Close & Return to Auction";

    // Item Lore on Auction Listings
    public static final String LORE_AUCTION_PRICE = "&#E0F8FF▪ Price: &#39FF14%s%s";
    public static final String LORE_AUCTION_SELLER = "&#E0F8FF▪ Seller: &#1DA1F2%s";
    public static final String LORE_AUCTION_TIME = "&#E0F8FF▪ Time Left: &#FFE600%s";
    public static final String LORE_AUCTION_SHULKER_HINT = "&#FF00BD[Right-Click] &#E0F8FFPreview Shulker Box";
    public static final String LORE_AUCTION_OWN_HINT = "&#FF0055&oʏᴏᴜʀ ʟɪꜱᴛɪɴɢ";
    public static final String LORE_MY_CANCEL_HINT = "&#FF0055&oᴄʟɪᴄᴋ ᴛᴏ ᴄᴀɴᴄᴇʟ ʟɪꜱᴛɪɴɢ";

    // Dialog Strings
    public static final String DIALOG_SEARCH_BODY = "Enter an item name or keyword to search player listings:";
    public static final String DIALOG_SEARCH_PLACEHOLDER = "Type item name or keyword...";
    public static final String DIALOG_SEARCH_BTN = "Search";
    public static final String DIALOG_CANCEL_BTN = "Cancel";

    public static final String DIALOG_PRICE_PLACEHOLDER = "Enter price (e.g. 5000 or 50k)...";
    public static final String DIALOG_PRICE_CONTINUE_BTN = "Continue";
    public static final String DIALOG_PRICE_SELLING = "Selling: %s";
    public static final String DIALOG_PRICE_LIMITS = "Min: $%s | Max: $%s";

    // Chat / Notification Messages
    public static final String MSG_PLAYER_ONLY = "&#FF0055This command can only be executed by players!";
    public static final String MSG_HOLD_ITEM_TO_SELL = "&#FF0055You must hold an item in your main hand to sell!";
    public static final String MSG_ITEM_BLOCKED = "&#FF0055Item &#FFE600%s &#FF0055cannot be sold on Auction!";
    public static final String MSG_MIN_PRICE = "&#FF0055Minimum auction price is &#39FF14%s%s&#FF0055!";
    public static final String MSG_MAX_PRICE = "&#FF0055Maximum auction price is &#39FF14%s%s&#FF0055!";
    public static final String MSG_SLOTS_FULL = "&#FF0055Your listing slots are full! (Maximum %d items)";

    public static final String MSG_LIST_SUCCESS = "&#39FF14✔ &7Successfully listed &#FF00BD%s &7for &#39FF14%s%s&7!";
    public static final String MSG_LIST_CANCELLED = "&#39FF14✔ &#E0F8FFListing cancelled and item returned to your inventory.";
    public static final String MSG_LIST_NOT_ACTIVE = "&#FF0055This listing is no longer active!";

    public static final String MSG_ITEM_NO_LONGER_AVAILABLE = "&#FF0055This item is no longer available!";
    public static final String MSG_CANNOT_BUY_OWN = "&#FF0055You cannot purchase your own listing!";
    public static final String MSG_OWN_LISTING_HINT = "&#FFE600This is your own listing! View &#1DA1F2'Your Items' &#FFE600to cancel it.";
    public static final String MSG_INSUFFICIENT_BALANCE = "&#FF0055Insufficient balance to purchase this item! (&#39FF14%s%s&#FF0055)";
    public static final String MSG_BUY_SUCCESS = "&#39FF14✔ &#E0F8FFSuccessfully purchased &#FF00BD%s &#E0F8FFfor &#39FF14%s%s&#E0F8FF!";
    public static final String MSG_SELLER_NOTIFIED = "&#39FF14✔ &#E0F8FFYour listing &#FF00BD%s &#E0F8FFwas purchased by &#1DA1F2%s &#E0F8FFfor &#39FF14%s%s";

    public static final String MSG_INVENTORY_FULL_DROPPED = "&#FFE600Inventory full! Item dropped at your feet.";
    public static final String MSG_INSERT_EMPTY = "&#FF0055Please place an item in the middle slot first!";
    public static final String MSG_NOT_A_SHULKER = "&#FF0055This item is not a Shulker Box!";

    public static Component component(String text) {
        return ColorUtils.parse(text);
    }

    public static Component formatComponent(String template, Object... args) {
        return ColorUtils.parse(String.format(template, args));
    }

    public static List<Component> componentList(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<Component> components = new ArrayList<>(list.size());
        for (String line : list) {
            components.add(ColorUtils.parseItem(line));
        }
        return components;
    }

    public static String getItemName(@Nullable ItemStack item) {
        if (item == null) return "Item";
        if (item.hasItemMeta()) {
            Component dn = item.getItemMeta().displayName();
            if (dn != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(dn);
                if (!plain.isBlank()) return plain;
            }
        }
        String raw = item.getType().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        String[] words = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static String getItemFormattedName(@Nullable ItemStack item) {
        if (item == null) return "Item";
        String name = getItemName(item);
        if (name.matches("(?i)^\\d+\\s*x\\s+.*")) {
            return name;
        }
        int amount = item.getAmount();
        if (amount > 1) {
            return amount + "x " + name;
        }
        return name;
    }
}
