package dev.slowy.core.report;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Representasi teks, warna, dan lore untuk Report System GUI & Dialog.
 */
@NullMarked
public final class ReportText {

    private ReportText() {}

    public static final String GUI_TITLE = "&#1DA1F2ʟᴀᴘᴏʀᴀɴ ᴘʟᴀʏᴇʀ";
    public static final String PREFIX_ACTIVE = "&#39FF14▪ ";
    public static final String PREFIX_INACTIVE = "&7▪ ";

    // Slot 0 - Batal
    public static final String BTN_CANCEL = "&#FF0055✖ ʙᴀᴛᴀʟ";
    public static final List<String> LORE_CANCEL = List.of(
            "&7Batalkan formulir laporan ini dan",
            "&7hapus seluruh draf sementara.",
            "",
            "&cKlik untuk membatalkan"
    );

    // Slot 1 - Kategori
    public static final String BTN_CATEGORY = "&#1DA1F2ᴋᴀᴛᴇɢᴏʀɪ ᴍᴀꜱᴀʟᴀʜ";

    // Slot 2 - Isi Pesan
    public static final String BTN_MESSAGE_EMPTY = "&#FFE600ᴛᴜʟɪꜱ ᴘᴇꜱᴀɴ &8(Wajib)";
    public static final List<String> LORE_MESSAGE_EMPTY = List.of(
            "&7Tuliskan detail kendala, bukti,",
            "&7atau kronologi kejadian secara rinci.",
            "",
            "&eKlik untuk mengetik pesan..."
    );

    public static final String BTN_MESSAGE_FILLED = "&#39FF14✔ ᴘᴇꜱᴀɴ ᴛᴇʀꜱɪᴍᴘᴀɴ";

    // Slot 3 - Urgensi
    public static final String BTN_URGENCY = "&#FFE600ᴛɪɴɢᴋᴀᴛ ᴜʀɢᴇɴꜱɪ";

    // Slot 4 - Kirim
    public static final String BTN_SUBMIT_LOCKED = "&7ᴋɪʀɪᴍ ʟᴀᴘᴏʀᴀɴ &8(Terkunci)";
    public static final List<String> LORE_SUBMIT_LOCKED = List.of(
            "&cHarap isi pesan laporan terlebih",
            "&cdahulu pada slot buku di samping!"
    );

    public static final String BTN_SUBMIT_READY = "&#39FF14✔ ᴋɪʀɪᴍ ʟᴀᴘᴏʀᴀɴ";
    public static final List<String> LORE_SUBMIT_READY = List.of(
            "&7Draf laporan Anda telah lengkap.",
            "&7Klik untuk mengirimkan ke staf server.",
            "",
            "&aKlik untuk mengirim laporan"
    );

    // Screen Dialog Texts
    public static final String DIALOG_TITLE = "&#1DA1F2<bold>Detail Pesan Laporan</bold>";
    public static final String DIALOG_INPUT_PLACEHOLDER = "Ketik penjelasan laporan Anda di sini...";
    public static final String DIALOG_SAVE_BTN = "✔ Simpan Pesan";
    public static final String DIALOG_CANCEL_BTN = "✖ Kembali";

    // Messages
    public static final String MSG_CANCELLED = "<red>✖ Laporan dibatalkan. Draf telah dihapus dari memori.</red>";
    public static final String MSG_NEED_MESSAGE = "<red>⚠ Harap isi pesan laporan terlebih dahulu melalui slot buku!</red>";
    public static final String MSG_SUBMIT_SUCCESS = "<green>✔ Laporan Anda berhasil dikirim ke antrean staf!</green>";
    public static final String MSG_MESSAGE_SAVED = "<green>✔ Pesan laporan berhasil disimpan ke draf!</green>";
}
