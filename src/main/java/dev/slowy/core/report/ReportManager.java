package dev.slowy.core.report;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pengelola sistem pelaporan Hopper GUI 5-Slot dan Paper Custom Screen Dialog.
 */
@NullMarked
public final class ReportManager implements Lifecycle {

    private final SlowyCore plugin;
    private final Map<UUID, ReportDraft> draftMap = new ConcurrentHashMap<>();

    public ReportManager(SlowyCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public ReportDraft getOrCreateDraft(UUID uuid) {
        return draftMap.computeIfAbsent(uuid, ReportDraft::new);
    }

    public @Nullable ReportDraft getDraft(UUID uuid) {
        return draftMap.get(uuid);
    }

    public void removeDraft(UUID uuid) {
        draftMap.remove(uuid);
    }

    public void openReportGui(Player player) {
        ReportDraft draft = getOrCreateDraft(player.getUniqueId());
        draft.setInDialog(false);

        ReportHolder holder = new ReportHolder(draft);
        Inventory inv = Bukkit.createInventory(holder, InventoryType.HOPPER, ColorUtils.parse(ReportText.GUI_TITLE));
        holder.setInventory(inv);

        renderGui(inv, draft);
        player.openInventory(inv);
    }

    public void renderGui(Inventory inv, ReportDraft draft) {
        // [ Slot 0 ] - RED_STAINED_GLASS_PANE (Batal)
        inv.setItem(0, createItem(Material.RED_STAINED_GLASS_PANE, ReportText.BTN_CANCEL, ReportText.LORE_CANCEL));

        // [ Slot 1 ] - NAME_TAG (Kategori Masalah - Cycle on Click)
        List<String> catLore = new ArrayList<>();
        catLore.add("&7Pilih jenis kategori masalah:");
        for (ReportCategory cat : ReportCategory.values()) {
            boolean active = cat == draft.getCategory();
            catLore.add((active ? ReportText.PREFIX_ACTIVE : ReportText.PREFIX_INACTIVE) +
                    (active ? cat.getFormattedName() : "&7" + cat.getDisplayName()));
        }
        catLore.add("");
        catLore.add("&eKlik untuk mengganti pilihan");
        inv.setItem(1, createItem(Material.NAME_TAG, ReportText.BTN_CATEGORY, catLore));

        // [ Slot 2 ] - WRITABLE_BOOK -> ENCHANTED_BOOK (Isi Pesan)
        if (!draft.hasMessage()) {
            inv.setItem(2, createItem(Material.WRITABLE_BOOK, ReportText.BTN_MESSAGE_EMPTY, ReportText.LORE_MESSAGE_EMPTY));
        } else {
            List<String> msgLore = new ArrayList<>();
            msgLore.add("&7Pratinjau cuplikan isi laporan:");
            String msg = Objects.requireNonNull(draft.getMessage());
            String[] lines = msg.split("\n");
            for (int i = 0; i < Math.min(lines.length, 3); i++) {
                String line = lines[i].trim();
                if (line.length() > 36) {
                    line = line.substring(0, 33) + "...";
                }
                msgLore.add("&f\"" + line + "\"");
            }
            if (lines.length > 3) {
                msgLore.add("&8... (" + (lines.length - 3) + " baris lainnya)");
            }
            msgLore.add("");
            msgLore.add("&eKlik untuk mengedit pesan");
            inv.setItem(2, createItem(Material.ENCHANTED_BOOK, ReportText.BTN_MESSAGE_FILLED, msgLore));
        }

        // [ Slot 3 ] - ENDER_EYE / DYE (Tingkat Urgensi - Cycle on Click)
        List<String> urgLore = new ArrayList<>();
        urgLore.add("&7Pilih tingkat urgensi penanganan:");
        for (ReportUrgency urg : ReportUrgency.values()) {
            boolean active = urg == draft.getUrgency();
            urgLore.add((active ? ReportText.PREFIX_ACTIVE : ReportText.PREFIX_INACTIVE) +
                    (active ? urg.getFormattedName() : "&7" + urg.getDisplayName()) +
                    " &8(" + urg.getDescription() + ")");
        }
        urgLore.add("");
        urgLore.add("&eKlik untuk mengganti prioritas");
        inv.setItem(3, createItem(draft.getUrgency().getMaterial(), ReportText.BTN_URGENCY, urgLore));

        // [ Slot 4 ] - LIME_STAINED_GLASS_PANE / GRAY_STAINED_GLASS_PANE (Kirim Laporan)
        if (draft.hasMessage()) {
            inv.setItem(4, createItem(Material.LIME_STAINED_GLASS_PANE, ReportText.BTN_SUBMIT_READY, ReportText.LORE_SUBMIT_READY));
        } else {
            inv.setItem(4, createItem(Material.GRAY_STAINED_GLASS_PANE, ReportText.BTN_SUBMIT_LOCKED, ReportText.LORE_SUBMIT_LOCKED));
        }
    }

    public void openMessageDialog(Player player, ReportDraft draft) {
        draft.setInDialog(true);
        player.closeInventory();

        String currentMsg = draft.getMessage() != null ? draft.getMessage() : "";

        TextDialogInput messageInput = DialogInput.text("report_message", Component.text(ReportText.DIALOG_INPUT_PLACEHOLDER))
                .initial(currentMsg)
                .width(320)
                .maxLength(1000)
                .multiline(TextDialogInput.MultilineOptions.create(8, 100))
                .build();

        ActionButton saveBtn = ActionButton.builder(Component.text(ReportText.DIALOG_SAVE_BTN, NamedTextColor.GREEN, TextDecoration.BOLD))
                .width(130)
                .action(DialogAction.customClick((responseView, audience) -> {
                    if (audience instanceof Player p) {
                        String text = responseView.getText("report_message");
                        draft.setInDialog(false);
                        if (text != null && !text.isBlank()) {
                            draft.setMessage(text.trim());
                            p.sendMessage(ColorUtils.parse(ReportText.MSG_MESSAGE_SAVED));
                            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
                        } else {
                            draft.setMessage(null);
                        }
                        openReportGui(p);
                    }
                }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build()))
                .build();

        ActionButton cancelBtn = ActionButton.builder(Component.text(ReportText.DIALOG_CANCEL_BTN, NamedTextColor.RED))
                .width(100)
                .action(DialogAction.customClick((responseView, audience) -> {
                    if (audience instanceof Player p) {
                        draft.setInDialog(false);
                        openReportGui(p);
                    }
                }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build()))
                .build();

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(ColorUtils.parse("<gray>Kategori:</gray> " + draft.getCategory().getFormattedName() +
                " <dark_gray>|</dark_gray> <gray>Urgensi:</gray> " + draft.getUrgency().getFormattedName())));
        bodies.add(DialogBody.plainMessage(ColorUtils.parse("<gray>Tuliskan deskripsi atau bukti laporan Anda secara lengkap:</gray>")));

        DialogBase base = DialogBase.builder(ColorUtils.parse(ReportText.DIALOG_TITLE))
                .body(bodies)
                .inputs(List.of(messageInput))
                .pause(false)
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(saveBtn, cancelBtn))
                .columns(2)
                .build();

        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    public void cancelReport(Player player) {
        removeDraft(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ColorUtils.parse(ReportText.MSG_CANCELLED));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
    }

    public void submitReport(Player player, ReportDraft draft) {
        if (!draft.hasMessage()) {
            player.sendMessage(ColorUtils.parse(ReportText.MSG_NEED_MESSAGE));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        // Placeholder alert & konfirmasi lengkap sesuai spec
        player.sendMessage(ColorUtils.parse(ReportText.MSG_SUBMIT_SUCCESS));
        player.sendMessage(ColorUtils.parse("<gray>• Kategori:</gray> " + draft.getCategory().getFormattedName()));
        player.sendMessage(ColorUtils.parse("<gray>• Urgensi:</gray> " + draft.getUrgency().getFormattedName()));
        player.sendMessage(Component.text("• Isi Laporan: ", NamedTextColor.GRAY)
                .append(Component.text(Objects.requireNonNull(draft.getMessage()), NamedTextColor.WHITE)));
        player.sendMessage(ColorUtils.parse("<dark_gray><i>(Status: Draf diterima - Pengiriman antrean staf TODO)</i></dark_gray>"));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);

        removeDraft(player.getUniqueId());
        player.closeInventory();
    }

    private ItemStack createItem(Material mat, String name, @Nullable List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(meta -> {
            meta.displayName(ColorUtils.parseItem(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(ColorUtils.parseItemLore(loreLines));
            }
        });
        return item;
    }

    @Override
    public void onDisable() {
        draftMap.clear();
    }
}
