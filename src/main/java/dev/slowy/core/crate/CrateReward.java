package dev.slowy.core.crate;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

@NullMarked
public record CrateReward(
        String id,
        String displayName,
        List<String> displayLore,
        RewardType rewardType,
        @Nullable Material material,
        int amount,
        Map<Enchantment, Integer> enchants,
        List<String> commands,
        double moneyAmount,
        int weight
) {

    public enum RewardType { ITEM, COMMAND, MONEY, POTION, TOOL }

    public CrateReward {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        displayLore = displayLore != null ? List.copyOf(displayLore) : List.of();
        enchants = enchants != null ? Map.copyOf(enchants) : Map.of();
        commands = commands != null ? List.copyOf(commands) : List.of();
        amount = Math.max(1, amount);
        weight = Math.max(1, weight);
    }

    public static CrateReward item(String id, String name, Material mat, int amount, int weight) {
        return new CrateReward(id, name, List.of(), RewardType.ITEM, mat, amount, Map.of(), List.of(), 0, weight);
    }

    public static CrateReward enchantedItem(String id, String name, Material mat, int amount, Map<Enchantment, Integer> enchants, int weight) {
        return new CrateReward(id, name, List.of(), RewardType.ITEM, mat, amount, enchants, List.of(), 0, weight);
    }

    public static CrateReward money(String id, String name, double amount, int weight) {
        return new CrateReward(id, name, List.of(), RewardType.MONEY, null, 1, Map.of(), List.of(), amount, weight);
    }

    public static CrateReward potion(String id, String name, int weight) {
        return new CrateReward(id, name, List.of(), RewardType.POTION, null, 1, Map.of(), List.of(), 0, weight);
    }

    public static CrateReward tool(String id, String name, List<String> lore, int weight) {
        return new CrateReward(id, name, lore, RewardType.TOOL, null, 1, Map.of(), List.of(), 0, weight);
    }

    public ItemStack createDisplayItem(SlowyCore plugin) {
        return switch (rewardType) {
            case MONEY -> {
                ItemStack item = new ItemStack(Material.GOLD_INGOT);
                item.editMeta(m -> {
                    m.displayName(ColorUtils.parseItem(displayName));
                    m.setEnchantmentGlintOverride(true);
                });
                yield item;
            }
            case TOOL -> Objects.requireNonNullElseGet(createCustomTool(plugin, id), () -> new ItemStack(Material.NETHERITE_SWORD));
            case POTION -> Objects.requireNonNullElseGet(createPotionItem(id), () -> new ItemStack(Material.POTION));
            case ITEM, COMMAND -> {
                Material mat = material != null ? material : Material.CHEST;
                ItemStack item = new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
                if (!enchants.isEmpty()) {
                    item.editMeta(m -> enchants.forEach((e, lvl) -> m.addEnchant(e, lvl, true)));
                }
                yield item;
            }
        };
    }

    public void grant(SlowyCore plugin, Player player) {
        switch (rewardType) {
            case MONEY -> {
                if (moneyAmount > 0) {
                    plugin.getEconomyManager().deposit(player.getUniqueId(), moneyAmount);
                    player.sendMessage(ColorUtils.parse("<#39FF14>✔ Received <#E0F8FF>$" + plugin.getEconomyManager().formatNicest(moneyAmount) + "</#E0F8FF>!</#39FF14>"));
                }
            }
            case TOOL -> {
                ItemStack tool = createCustomTool(plugin, id);
                if (tool != null) giveOrDrop(player, tool);
            }
            case POTION -> {
                ItemStack pot = createPotionItem(id);
                if (pot != null) giveOrDrop(player, pot);
            }
            case COMMAND -> {
                for (String cmd : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
                }
            }
            case ITEM -> {
                Material mat = material != null ? material : Material.CHEST;
                ItemStack item = new ItemStack(mat, amount);
                if (!enchants.isEmpty()) {
                    item.editMeta(m -> enchants.forEach((e, lvl) -> m.addEnchant(e, lvl, true)));
                }
                giveOrDrop(player, item);
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack rem : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rem);
            }
            player.sendMessage(ColorUtils.parse("<#FFE600>⚠ Inventory full! Some items dropped to the ground.</#FFE600>"));
        }
    }

    public static @Nullable ItemStack createCustomTool(SlowyCore plugin, String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "amethyst_chopper" -> createTool(plugin, "CHOPPER", Material.NETHERITE_AXE, "<#FF00BD>Amethyst Tree Chopper</#FF00BD>",
                    List.of("<gray>Silk Touch</gray>", "<gray>Efficiency V</gray>", "<gray>Unbreaking III</gray>", "<gray>Mending</gray>", "", "<#FF00BD>Breaks Trees Instantly</#FF00BD>", "<dark_gray>Self Destruct: 3d</dark_gray>"),
                    Map.of(Enchantment.SILK_TOUCH, 1, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), 259200L);

            case "amethyst_sell_axe" -> createTool(plugin, "SELL_AXE", Material.NETHERITE_AXE, "<#FF00BD>Amethyst Sell Axe</#FF00BD>",
                    List.of("<gray>Efficiency V</gray>", "<gray>Unbreaking III</gray>", "<gray>Mending</gray>", "", "<#FF00BD>Instantly Sells Chests</#FF00BD>", "<dark_gray>Self Destruct: 3d</dark_gray>"),
                    Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), 259200L);

            case "amethyst_drill" -> createTool(plugin, "DRILL", Material.NETHERITE_PICKAXE, "<#FF00BD>Amethyst Pickaxe</#FF00BD>",
                    List.of("<gray>Efficiency V</gray>", "<gray>Unbreaking III</gray>", "<gray>Mending</gray>", "", "<#FF00BD>Breaks 9 Blocks at Once</#FF00BD>", "<dark_gray>Self Destruct: 3d</dark_gray>"),
                    Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), 259200L);

            case "amethyst_shovel" -> createTool(plugin, "SHOVEL", Material.NETHERITE_SHOVEL, "<#FF00BD>Amethyst Shovel</#FF00BD>",
                    List.of("<gray>Efficiency V</gray>", "<gray>Unbreaking III</gray>", "<gray>Fortune III</gray>", "", "<#FF00BD>Breaks 9 Soft Blocks</#FF00BD>", "<dark_gray>Self Destruct: 3d</dark_gray>"),
                    Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 3), 259200L);

            case "shard_booster" -> {
                ItemStack it = new ItemStack(Material.POTION);
                it.editMeta(PotionMeta.class, meta -> {
                    meta.setBasePotionType(PotionType.INVISIBILITY);
                    meta.displayName(ColorUtils.parseItem("<#FF00BD>Shard Booster</#FF00BD>"));
                    meta.lore(List.of(
                            ColorUtils.parseItem("<#FF00BD>Drink to activate</#FF00BD>"),
                            ColorUtils.parseItem(""),
                            ColorUtils.parseItem("<#FF00BD>4x Shard Multiplier</#FF00BD>"),
                            ColorUtils.parseItem("<dark_gray>Self Destruct: 1h</dark_gray>")
                    ));
                    meta.setEnchantmentGlintOverride(true);
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "amethyst_type"), PersistentDataType.STRING, "SHARD_BOOSTER");
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "amethyst_expiry"), PersistentDataType.LONG, System.currentTimeMillis() + 3600_000L);
                });
                it.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.POTION_CONTENTS));
                yield it;
            }
            default -> null;
        };
    }

    private static ItemStack createTool(SlowyCore plugin, String typeKey, Material mat, String name, List<String> lore, Map<Enchantment, Integer> enchants, long durSec) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> {
            m.displayName(ColorUtils.parseItem(name));
            m.lore(lore.stream().map(ColorUtils::parseItem).toList());
            enchants.forEach((e, lvl) -> m.addEnchant(e, lvl, true));
            m.setEnchantmentGlintOverride(true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            m.getPersistentDataContainer().set(new NamespacedKey(plugin, "amethyst_type"), PersistentDataType.STRING, typeKey);
            m.getPersistentDataContainer().set(new NamespacedKey(plugin, "amethyst_expiry"), PersistentDataType.LONG, System.currentTimeMillis() + (durSec * 1000L));
        });
        return item;
    }

    private static @Nullable ItemStack createPotionItem(String id) {
        return switch (id) {
            case "potion_heal" -> createPotion(Material.POTION, PotionType.STRONG_HEALING);
            case "potion_strength" -> createPotion(Material.POTION, PotionType.STRONG_STRENGTH);
            case "potion_splash_strength" -> createPotion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH);
            default -> null;
        };
    }

    private static ItemStack createPotion(Material mat, PotionType type) {
        ItemStack pot = new ItemStack(mat);
        pot.editMeta(PotionMeta.class, m -> m.setBasePotionType(type));
        return pot;
    }
}
