package dev.slowy.core.rtp;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class RtpMenu {

    public static final class RtpHolder implements InventoryHolder {
        private @Nullable Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory != null ? inventory : Bukkit.createInventory(null, 9);
        }
    }

    private static final Component TITLE_COMPONENT = ColorUtils.parse(CoreConfig.RTP_MENU_TITLE);

    private static final ItemStack ITEM_OVERWORLD = createStaticItem(
            CoreConfig.RTP_ITEM_OVERWORLD, CoreConfig.RTP_COLOR_OVERWORLD + CoreConfig.RTP_LABEL_OVERWORLD);
    private static final ItemStack ITEM_NETHER = createStaticItem(
            CoreConfig.RTP_ITEM_NETHER, CoreConfig.RTP_COLOR_NETHER + CoreConfig.RTP_LABEL_NETHER);
    private static final ItemStack ITEM_END = createStaticItem(
            CoreConfig.RTP_ITEM_END, CoreConfig.RTP_COLOR_END + CoreConfig.RTP_LABEL_THE_END);

    private static final ItemStack[] TEMPLATE_CONTENTS = new ItemStack[CoreConfig.RTP_MENU_SIZE];

    static {
        TEMPLATE_CONTENTS[CoreConfig.RTP_SLOT_OVERWORLD] = ITEM_OVERWORLD;
        TEMPLATE_CONTENTS[CoreConfig.RTP_SLOT_NETHER] = ITEM_NETHER;
        TEMPLATE_CONTENTS[CoreConfig.RTP_SLOT_END] = ITEM_END;
    }

    private final SlowyCore plugin;

    public RtpMenu(SlowyCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (!player.isOnline()) return;

        if (!CoreConfig.isHubWorld(player.getWorld())) {
            // Pemain di luar spawn/afk: langsung RTP di dimensi tempat pemain berada
            player.closeInventory();
            RtpWorldType currentType = RtpWorldType.fromEnvironment(player.getWorld().getEnvironment());
            plugin.getRtpManager().teleportRandomSafe(player, currentType);
            return;
        }

        player.closeInventory();
        RtpHolder holder = new RtpHolder();
        Inventory inv = Bukkit.createInventory(holder, CoreConfig.RTP_MENU_SIZE, TITLE_COMPONENT);
        holder.setInventory(inv);
        inv.setContents(TEMPLATE_CONTENTS);

        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        } catch (Throwable ignored) {}

        player.openInventory(inv);
    }

    public void handleClick(Player player, int slot) {
        if (!player.isOnline()) return;

        if (!CoreConfig.isHubWorld(player.getWorld())) {
            player.closeInventory();
            player.sendMessage(ColorUtils.parse(CoreConfig.RTP_MSG_ONLY_IN_SPAWN));
            return;
        }

        RtpWorldType targetType = switch (slot) {
            case CoreConfig.RTP_SLOT_OVERWORLD -> RtpWorldType.OVERWORLD;
            case CoreConfig.RTP_SLOT_NETHER -> RtpWorldType.NETHER;
            case CoreConfig.RTP_SLOT_END -> RtpWorldType.THE_END;
            default -> null;
        };

        if (targetType != null) {
            player.closeInventory();
            plugin.getRtpManager().teleportRandomSafe(player, targetType);
        } else {
            return;
        }

        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
        } catch (Throwable ignored) {}
    }

    private static ItemStack createStaticItem(Material material, String miniMessageName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.parseItem(miniMessageName));
            item.setItemMeta(meta);
        }
        return item;
    }
}
