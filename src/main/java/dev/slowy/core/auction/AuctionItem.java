package dev.slowy.core.auction;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NullMarked
public class AuctionItem {

    private final long id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long createdAt;
    private final long expiresAt;
    private String status;
    private @Nullable UUID buyerUuid;
    private @Nullable String buyerName;
    private long soldAt;

    public AuctionItem(long id, UUID sellerUuid, String sellerName, ItemStack item,
                       double price, long createdAt, long expiresAt, String status,
                       @Nullable UUID buyerUuid, @Nullable String buyerName, long soldAt) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName != null ? sellerName : "Unknown";
        this.item = item;
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status != null ? status : "ACTIVE";
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName;
        this.soldAt = soldAt;
    }

    public long getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getPrice() {
        return price;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public @Nullable UUID getBuyerUuid() {
        return buyerUuid;
    }

    public void setBuyerUuid(@Nullable UUID buyerUuid) {
        this.buyerUuid = buyerUuid;
    }

    public @Nullable String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(@Nullable String buyerName) {
        this.buyerName = buyerName;
    }

    public long getSoldAt() {
        return soldAt;
    }

    public void setSoldAt(long soldAt) {
        this.soldAt = soldAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public boolean isShulkerBox() {
        return item != null && item.getType().name().endsWith("SHULKER_BOX");
    }

    public String getTimeLeftFormatted() {
        long diff = expiresAt - System.currentTimeMillis();
        if (diff <= 0) return "Expired";

        long totalSeconds = diff / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", Math.max(1, seconds));
        }
    }

    public ItemStack buildDisplayItem(SlowyCore plugin, @Nullable Player viewer) {
        if (item == null) return new ItemStack(org.bukkit.Material.AIR);
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        String sym = plugin.getEconomyManager().getCurrencySymbol();
        String formattedPrice = plugin.getEconomyManager().formatNicest(price);

        lore.add(Component.empty());
        lore.add(ColorUtils.parseItem(String.format(AuctionText.LORE_AUCTION_PRICE, sym, formattedPrice)));
        lore.add(ColorUtils.parseItem(String.format(AuctionText.LORE_AUCTION_SELLER, sellerName)));
        lore.add(ColorUtils.parseItem(String.format(AuctionText.LORE_AUCTION_TIME, getTimeLeftFormatted())));

        if (isShulkerBox()) {
            lore.add(ColorUtils.parseItem(AuctionText.LORE_AUCTION_SHULKER_HINT));
        }

        boolean isSeller = viewer != null && viewer.getUniqueId().equals(sellerUuid);
        if (isSeller) {
            lore.add(Component.empty());
            lore.add(ColorUtils.parseItem(AuctionText.LORE_AUCTION_OWN_HINT));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    public ItemStack buildMyListingDisplay(SlowyCore plugin) {
        if (item == null) return new ItemStack(org.bukkit.Material.AIR);
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        String sym = plugin.getEconomyManager().getCurrencySymbol();
        String formattedPrice = plugin.getEconomyManager().formatNicest(price);

        lore.add(Component.empty());
        lore.add(ColorUtils.parseItem(String.format(AuctionText.LORE_AUCTION_PRICE, sym, formattedPrice)));
        lore.add(ColorUtils.parseItem(String.format(AuctionText.LORE_AUCTION_TIME, getTimeLeftFormatted())));
        lore.add(Component.empty());
        lore.add(ColorUtils.parseItem(AuctionText.LORE_MY_CANCEL_HINT));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }
}
