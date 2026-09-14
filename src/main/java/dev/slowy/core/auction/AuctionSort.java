package dev.slowy.core.auction;

import org.jspecify.annotations.NullMarked;

import java.util.Comparator;

@NullMarked
public enum AuctionSort {
    HIGHEST_PRICE("Highest Price", Comparator.comparingDouble(AuctionItem::getPrice).reversed()),
    LOWEST_PRICE("Lowest Price", Comparator.comparingDouble(AuctionItem::getPrice)),
    LAST_LISTED("Last Listed", Comparator.comparingLong(AuctionItem::getCreatedAt)),
    RECENTLY_LISTED("Recently Listed", Comparator.comparingLong(AuctionItem::getCreatedAt).reversed());

    private final String displayName;
    private final Comparator<AuctionItem> comparator;

    AuctionSort(String displayName, Comparator<AuctionItem> comparator) {
        this.displayName = displayName;
        this.comparator = comparator;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Comparator<AuctionItem> getComparator() {
        return comparator;
    }

    public AuctionSort next() {
        AuctionSort[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
