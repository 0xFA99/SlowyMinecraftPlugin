package dev.slowy.core.auction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

@NullMarked
public enum AuctionCategory {
    ALL("All"),
    BLOCKS("Blocks"),
    TOOLS("Tools"),
    FOOD("Food"),
    COMBAT("Combat"),
    POTIONS("Potions"),
    BOOKS("Books"),
    INGREDIENTS("Ingredients"),
    UTILITIES("Utilities");

    private final String displayName;
    private static final Map<Material, AuctionCategory> CATEGORY_CACHE = new EnumMap<>(Material.class);

    static {
        // Pre-kalkulasi 1x saat kelas dimuat ke memori (O(1) runtime lookup)
        for (Material mat : Material.values()) {
            if (mat.isAir()) continue;
            CATEGORY_CACHE.put(mat, computeCategory(mat));
        }
    }

    AuctionCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean matches(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        if (this == ALL) return true;
        return CATEGORY_CACHE.getOrDefault(item.getType(), UTILITIES) == this;
    }

    private static AuctionCategory computeCategory(Material type) {
        String name = type.name();

        if (name.endsWith("_POTION") || name.endsWith("_BOTTLE") || name.contains("POTION") || name.contains("OMINOUS_BOTTLE")) {
            return POTIONS;
        }
        if (type.isEdible()) {
            return FOOD;
        }
        if (type.isBlock()) {
            return BLOCKS;
        }
        if (name.equals("ENCHANTED_BOOK") || name.equals("BOOK") || name.equals("WRITTEN_BOOK") || name.equals("WRITABLE_BOOK")) {
            return BOOKS;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("BOW") || name.equals("CROSSBOW")
                || name.equals("TRIDENT") || name.equals("MACE") || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("ELYTRA") || name.equals("SHIELD")
                || name.endsWith("_TURTLE_HELMET") || name.endsWith("_HORSE_ARMOR") || name.endsWith("_WOLF_ARMOR")
                || name.endsWith("_ARROW") || name.equals("TOTEM_OF_UNDYING")) {
            return COMBAT;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.equals("FISHING_ROD")
                || name.equals("SHEARS") || name.equals("FLINT_AND_STEEL") || name.equals("BRUSH") || name.equals("SPYGLASS")
                || name.equals("COMPASS") || name.equals("RECOVERY_COMPASS") || name.equals("CLOCK") || name.equals("LEAD")) {
            return TOOLS;
        }
        if (isIngredient(name)) {
            return INGREDIENTS;
        }
        return UTILITIES;
    }

    private static boolean isIngredient(String name) {
        if (name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.startsWith("RAW_")
                || name.endsWith("_ORE") || name.endsWith("_TEMPLATE") || name.endsWith("_POTTERY_SHERD")) {
            return true;
        }
        return switch (name) {
            case "DIAMOND", "EMERALD", "NETHERITE_SCRAP", "AMETHYST_SHARD", "QUARTZ",
                 "LAPIS_LAZULI", "REDSTONE", "GLOWSTONE_DUST", "COAL", "CHARCOAL",
                 "BLAZE_ROD", "BLAZE_POWDER", "GUNPOWDER", "STRING", "FEATHER",
                 "LEATHER", "RABBIT_HIDE", "BONE", "SLIME_BALL", "MAGMA_CREAM",
                 "GHAST_TEAR", "ENDER_PEARL", "EYE_OF_ENDER", "SHULKER_SHELL",
                 "PHANTOM_MEMBRANE", "BREEZE_ROD", "HEAVY_CORE", "NETHER_STAR",
                 "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "SUGAR", "WHEAT",
                 "EGG", "HONEYCOMB", "STICK", "FLINT", "CLAY_BALL", "BRICK",
                 "NETHER_BRICK", "NETHER_WART", "FERMENTED_SPIDER_EYE", "SPIDER_EYE",
                 "GLISTERING_MELON_SLICE", "GOLDEN_CARROT", "RABBIT_FOOT",
                 "TURTLE_SCUTE", "ARMADILLO_SCUTE", "RESIN_CLUMP", "RESIN_BRICK" -> true;
            default -> false;
        };
    }

    public AuctionCategory next() {
        AuctionCategory[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
