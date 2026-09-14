package dev.slowy.core.crate;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@NullMarked
public final class CrateDefinition {

    public enum OpenType {
        GACHA, CHOOSE_ONE
    }

    private final String id;
    private final String displayName;
    private final String colorHex;
    private final String keyDisplayName;
    private final Material keyMaterial;
    private final List<String> keyLore;
    private final OpenType openType;
    private final boolean broadcastOnClaim;
    private final List<CrateReward> rewards;
    private final NamespacedKey keyPdc;
    private final int totalWeight;

    public CrateDefinition(
            SlowyCore plugin,
            String id,
            String displayName,
            String colorHex,
            String keyDisplayName,
            Material keyMaterial,
            List<String> keyLore,
            OpenType openType,
            boolean broadcastOnClaim,
            List<CrateReward> rewards
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.colorHex = Objects.requireNonNull(colorHex, "colorHex cannot be null");
        this.keyDisplayName = Objects.requireNonNull(keyDisplayName, "keyDisplayName cannot be null");
        this.keyMaterial = Objects.requireNonNull(keyMaterial, "keyMaterial cannot be null");
        this.keyLore = keyLore != null ? List.copyOf(keyLore) : List.of();
        this.openType = Objects.requireNonNull(openType, "openType cannot be null");
        this.broadcastOnClaim = broadcastOnClaim;
        this.rewards = rewards != null ? List.copyOf(rewards) : List.of();
        this.keyPdc = new NamespacedKey(plugin, "crate_key");

        // Precompute total weight once for O(1) random lookup start
        int sum = 0;
        for (CrateReward reward : this.rewards) {
            sum += reward.weight();
        }
        this.totalWeight = sum;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getColorHex() { return colorHex; }
    public String getKeyDisplayName() { return keyDisplayName; }
    public Material getKeyMaterial() { return keyMaterial; }
    public List<String> getKeyLore() { return keyLore; }
    public OpenType getOpenType() { return openType; }
    public boolean isBroadcastOnClaim() { return broadcastOnClaim; }
    public List<CrateReward> getRewards() { return rewards; }

    public String getFormattedKeyName() {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "common" -> "Common Key";
            case "uncommon" -> "Uncommon Key";
            case "rare" -> "Rare Key";
            case "epic" -> "Epic Key";
            case "legendary" -> "Legendary Key";
            default -> Character.toUpperCase(id.charAt(0)) + id.substring(1) + " Key";
        };
    }

    public ItemStack createPhysicalKey(int amount) {
        ItemStack item = new ItemStack(keyMaterial, Math.max(1, amount));
        item.editMeta(meta -> {
            meta.displayName(ColorUtils.parseItem(keyDisplayName));
            if (!keyLore.isEmpty()) {
                meta.lore(ColorUtils.parseItemLore(keyLore));
            }
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyPdc, PersistentDataType.STRING, id);
        });
        return item;
    }

    public boolean isPhysicalKey(@Nullable ItemStack item) {
        if (item == null || item.getType() != keyMaterial || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String val = meta.getPersistentDataContainer().get(keyPdc, PersistentDataType.STRING);
        return id.equalsIgnoreCase(val);
    }

    public CrateReward pickRandomReward() {
        if (rewards.isEmpty()) {
            throw new IllegalStateException("Crate " + id + " has no rewards defined");
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (CrateReward r : rewards) {
            current += r.weight();
            if (roll < current) {
                return r;
            }
        }
        return rewards.getFirst();
    }

    public static Map<String, CrateDefinition> createDefaultCrates(SlowyCore plugin) {
        Map<String, CrateDefinition> map = new LinkedHashMap<>();

        // 1. Common Crate
        List<CrateReward> commonRewards = List.of(
                CrateReward.item("iron_helmet", "<gray>Iron Helmet</gray>", Material.IRON_HELMET, 1, 10),
                CrateReward.item("iron_chestplate", "<gray>Iron Chestplate</gray>", Material.IRON_CHESTPLATE, 1, 10),
                CrateReward.item("iron_leggings", "<gray>Iron Leggings</gray>", Material.IRON_LEGGINGS, 1, 10),
                CrateReward.item("iron_boots", "<gray>Iron Boots</gray>", Material.IRON_BOOTS, 1, 10),
                CrateReward.item("iron_pickaxe", "<gray>Iron Pickaxe</gray>", Material.IRON_PICKAXE, 1, 10),
                CrateReward.item("iron_axe", "<gray>Iron Axe</gray>", Material.IRON_AXE, 1, 10),
                CrateReward.item("iron_sword", "<gray>Iron Sword</gray>", Material.IRON_SWORD, 1, 10),
                CrateReward.item("iron_shovel", "<gray>Iron Shovel</gray>", Material.IRON_SHOVEL, 1, 10),
                CrateReward.item("cooked_beef", "<red>32x Cooked Beef</red>", Material.COOKED_BEEF, 32, 15),
                CrateReward.item("bread", "<gold>64x Bread</gold>", Material.BREAD, 64, 15),
                CrateReward.item("iron_ingot", "<gray>32x Iron Ingot</gray>", Material.IRON_INGOT, 32, 12),
                CrateReward.item("coal", "<dark_gray>64x Coal</dark_gray>", Material.COAL, 64, 15),
                CrateReward.item("oak_log", "<yellow>32x Oak Log</yellow>", Material.OAK_LOG, 32, 15),
                CrateReward.item("torch", "<yellow>16x Torch</yellow>", Material.TORCH, 16, 15),
                CrateReward.money("money_common", "<#FFE600>$2,500</#FFE600>", 2500.0, 10)
        );
        map.put("common", new CrateDefinition(
                plugin, "common",
                "<#E0F8FF>ᴄᴏᴍᴍᴏɴ ᴄʀᴀᴛᴇ</#E0F8FF>",
                "#E0F8FF",
                "<#E0F8FF>Common Key</#E0F8FF>",
                Material.TRIPWIRE_HOOK,
                List.of("<gray>Opens the <#E0F8FF>Common Crate</#E0F8FF> at Spawn.</gray>"),
                OpenType.GACHA,
                false,
                commonRewards
        ));

        // 2. Uncommon Crate
        List<CrateReward> uncommonRewards = List.of(
                CrateReward.enchantedItem("iron_helmet_prot", "<gray>Iron Helmet</gray>", Material.IRON_HELMET, 1, Map.of(Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 2), 8),
                CrateReward.enchantedItem("iron_chestplate_prot", "<gray>Iron Chestplate</gray>", Material.IRON_CHESTPLATE, 1, Map.of(Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 2), 8),
                CrateReward.enchantedItem("iron_sword_sharp", "<gray>Iron Sword</gray>", Material.IRON_SWORD, 1, Map.of(Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 2), 8),
                CrateReward.enchantedItem("iron_pickaxe_eff", "<gray>Iron Pickaxe</gray>", Material.IRON_PICKAXE, 1, Map.of(Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 2), 8),
                CrateReward.item("diamond_helmet", "<aqua>Diamond Helmet</aqua>", Material.DIAMOND_HELMET, 1, 6),
                CrateReward.item("diamond_boots", "<aqua>Diamond Boots</aqua>", Material.DIAMOND_BOOTS, 1, 6),
                CrateReward.item("diamond_pickaxe", "<aqua>Diamond Pickaxe</aqua>", Material.DIAMOND_PICKAXE, 1, 6),
                CrateReward.item("diamond_sword", "<aqua>Diamond Sword</aqua>", Material.DIAMOND_SWORD, 1, 6),
                CrateReward.item("golden_carrot", "<gold>16x Golden Carrot</gold>", Material.GOLDEN_CARROT, 16, 10),
                CrateReward.potion("potion_heal", "<green>Potion of Healing II</green>", 8),
                CrateReward.item("diamond", "<aqua>8x Diamond</aqua>", Material.DIAMOND, 8, 8),
                CrateReward.item("gold_ingot", "<gold>16x Gold Ingot</gold>", Material.GOLD_INGOT, 16, 10),
                CrateReward.item("ender_pearl", "<light_purple>16x Ender Pearl</light_purple>", Material.ENDER_PEARL, 16, 8),
                CrateReward.item("saddle", "<gold>Saddle</gold>", Material.SADDLE, 1, 6),
                CrateReward.item("name_tag", "<white>Name Tag</white>", Material.NAME_TAG, 1, 6),
                CrateReward.item("anvil", "<gray>Anvil</gray>", Material.ANVIL, 1, 6),
                CrateReward.money("money_uncommon", "<#FFE600>$10,000</#FFE600>", 10000.0, 8)
        );
        map.put("uncommon", new CrateDefinition(
                plugin, "uncommon",
                "<#39FF14>ᴜɴᴄᴏᴍᴍᴏɴ ᴄʀᴀᴛᴇ</#39FF14>",
                "#39FF14",
                "<#39FF14>Uncommon Key</#39FF14>",
                Material.TRIPWIRE_HOOK,
                List.of("<gray>Opens the <#39FF14>Uncommon Crate</#39FF14> at Spawn.</gray>"),
                OpenType.GACHA,
                false,
                uncommonRewards
        ));

        // 3. Rare Crate
        List<CrateReward> rareRewards = List.of(
                CrateReward.enchantedItem("diamond_helmet_prot", "<aqua>Diamond Helmet</aqua>", Material.DIAMOND_HELMET, 1, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3), 7),
                CrateReward.enchantedItem("diamond_chestplate_prot", "<aqua>Diamond Chestplate</aqua>", Material.DIAMOND_CHESTPLATE, 1, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3), 7),
                CrateReward.enchantedItem("diamond_leggings_prot", "<aqua>Diamond Leggings</aqua>", Material.DIAMOND_LEGGINGS, 1, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3), 7),
                CrateReward.enchantedItem("diamond_boots_prot", "<aqua>Diamond Boots</aqua>", Material.DIAMOND_BOOTS, 1, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3), 7),
                CrateReward.enchantedItem("diamond_pickaxe_eff", "<aqua>Diamond Pickaxe</aqua>", Material.DIAMOND_PICKAXE, 1, Map.of(Enchantment.EFFICIENCY, 4, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3), 7),
                CrateReward.enchantedItem("diamond_sword_sharp", "<aqua>Diamond Sword</aqua>", Material.DIAMOND_SWORD, 1, Map.of(Enchantment.SHARPNESS, 4, Enchantment.UNBREAKING, 3), 7),
                CrateReward.enchantedItem("diamond_axe_eff", "<aqua>Diamond Axe</aqua>", Material.DIAMOND_AXE, 1, Map.of(Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 3), 7),
                CrateReward.enchantedItem("bow_power", "<gold>Bow</gold>", Material.BOW, 1, Map.of(Enchantment.POWER, 4, Enchantment.INFINITY, 1), 7),
                CrateReward.enchantedItem("crossbow", "<gold>Crossbow</gold>", Material.CROSSBOW, 1, Map.of(Enchantment.QUICK_CHARGE, 2, Enchantment.MULTISHOT, 1), 7),
                CrateReward.item("golden_carrot_rare", "<gold>32x Golden Carrot</gold>", Material.GOLDEN_CARROT, 32, 9),
                CrateReward.item("golden_apple", "<gold>4x Golden Apple</gold>", Material.GOLDEN_APPLE, 4, 8),
                CrateReward.potion("potion_strength", "<red>Potion of Strength II</red>", 7),
                CrateReward.item("diamond_rare", "<aqua>16x Diamond</aqua>", Material.DIAMOND, 16, 8),
                CrateReward.item("netherite_scrap", "<dark_purple>4x Netherite Scrap</dark_purple>", Material.NETHERITE_SCRAP, 4, 6),
                CrateReward.item("smithing_template", "<light_purple>Netherite Upgrade Template</light_purple>", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 5),
                CrateReward.item("ender_pearl_rare", "<light_purple>32x Ender Pearl</light_purple>", Material.ENDER_PEARL, 32, 8),
                CrateReward.item("firework_rocket", "<green>16x Firework Rocket</green>", Material.FIREWORK_ROCKET, 16, 8),
                CrateReward.item("totem_rare", "<light_purple>Totem of Undying</light_purple>", Material.TOTEM_OF_UNDYING, 1, 5),
                CrateReward.money("money_rare", "<#FFE600>$25,000</#FFE600>", 25000.0, 7)
        );
        map.put("rare", new CrateDefinition(
                plugin, "rare",
                "<#00F5FF>ʀᴀʀᴇ ᴄʀᴀᴛᴇ</#00F5FF>",
                "#00F5FF",
                "<#00F5FF>Rare Key</#00F5FF>",
                Material.TRIPWIRE_HOOK,
                List.of("<gray>Opens the <#00F5FF>Rare Crate</#00F5FF> at Spawn.</gray>"),
                OpenType.GACHA,
                false,
                rareRewards
        ));

        // 4. Epic Crate
        List<CrateReward> epicRewards = List.of(
                CrateReward.enchantedItem("diamond_helmet_max", "<aqua>Diamond Helmet</aqua>", Material.DIAMOND_HELMET, 1, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), 7),
                CrateReward.enchantedItem("diamond_chestplate_max", "<aqua>Diamond Chestplate</aqua>", Material.DIAMOND_CHESTPLATE, 1, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), 7),
                CrateReward.enchantedItem("netherite_helmet", "<dark_purple>Netherite Helmet</dark_purple>", Material.NETHERITE_HELMET, 1, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3), 6),
                CrateReward.enchantedItem("netherite_chestplate", "<dark_purple>Netherite Chestplate</dark_purple>", Material.NETHERITE_CHESTPLATE, 1, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3), 6),
                CrateReward.enchantedItem("netherite_sword", "<dark_purple>Netherite Sword</dark_purple>", Material.NETHERITE_SWORD, 1, Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), 6),
                CrateReward.enchantedItem("netherite_pickaxe", "<dark_purple>Netherite Pickaxe</dark_purple>", Material.NETHERITE_PICKAXE, 1, Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), 6),
                CrateReward.item("golden_apple_epic", "<gold>8x Golden Apple</gold>", Material.GOLDEN_APPLE, 8, 8),
                CrateReward.item("god_apple", "<gold>Enchanted Golden Apple</gold>", Material.ENCHANTED_GOLDEN_APPLE, 1, 4),
                CrateReward.potion("potion_splash_strength", "<red>Splash Potion of Strength II</red>", 7),
                CrateReward.item("netherite_ingot", "<dark_purple>2x Netherite Ingot</dark_purple>", Material.NETHERITE_INGOT, 2, 5),
                CrateReward.item("smithing_template_epic", "<light_purple>2x Netherite Upgrade Template</light_purple>", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2, 5),
                CrateReward.item("totem_epic", "<light_purple>2x Totem of Undying</light_purple>", Material.TOTEM_OF_UNDYING, 2, 5),
                CrateReward.item("beacon", "<aqua>Beacon</aqua>", Material.BEACON, 1, 4),
                CrateReward.item("shulker_box", "<light_purple>Shulker Box</light_purple>", Material.SHULKER_BOX, 1, 5),
                CrateReward.money("money_epic", "<#FFE600>$75,000</#FFE600>", 75000.0, 6)
        );
        map.put("epic", new CrateDefinition(
                plugin, "epic",
                "<#FF0055>ᴇᴘɪᴄ ᴄʀᴀᴛᴇ</#FF0055>",
                "#FF0055",
                "<#FF0055>Epic Key</#FF0055>",
                Material.TRIPWIRE_HOOK,
                List.of("<gray>Opens the <#FF0055>Epic Crate</#FF0055> at Spawn.</gray>"),
                OpenType.GACHA,
                false,
                epicRewards
        ));

        // 5. Legendary Crate
        List<CrateReward> legendaryRewards = List.of(
                CrateReward.tool("amethyst_chopper", "<#FF00BD>Amethyst Tree Chopper</#FF00BD>", List.of("&dBreaks Trees Instantly", "&8Self Destruct: 3d"), 20),
                CrateReward.tool("amethyst_sell_axe", "<#FF00BD>Amethyst Sell Axe</#FF00BD>", List.of("&dInstantly Sells Chests", "&8Self Destruct: 3d"), 20),
                CrateReward.tool("amethyst_drill", "<#FF00BD>Amethyst Pickaxe</#FF00BD>", List.of("&dBreaks 9 Blocks at Once", "&8Self Destruct: 3d"), 20),
                CrateReward.tool("amethyst_shovel", "<#FF00BD>Amethyst Shovel</#FF00BD>", List.of("&dBreaks 9 Soft Blocks", "&8Self Destruct: 3d"), 20),
                CrateReward.tool("shard_booster", "<#FF00BD>Shard Booster</#FF00BD>", List.of("&d4x Shard Multiplier", "&8Self Destruct: 1h"), 20)
        );
        map.put("legendary", new CrateDefinition(
                plugin, "legendary",
                "<#FF00BD>ʟᴇɢᴇɴᴅᴀʀʏ ᴄʀᴀᴛᴇ</#FF00BD>",
                "#FF00BD",
                "<#FF00BD>Legendary Key</#FF00BD>",
                Material.TRIPWIRE_HOOK,
                List.of("<gray>Opens the <#FF00BD>Legendary Crate</#FF00BD> at Spawn.</gray>"),
                OpenType.CHOOSE_ONE,
                true,
                legendaryRewards
        ));

        return Collections.unmodifiableMap(map);
    }
}
