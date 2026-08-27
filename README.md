# Slowy - Minecraft Paper Plugin & Datapack

Project resmi untuk server **Slowy SMP** (Paper 1.21.x) yang menggabungkan:
1. **Slowy Plugin (`Slowy.jar`)**:
   - Dynamic Dialog Homes System (Create, Teleport, Rename, Delete).
   - Dynamic Dialog Settings & Scoreboard Manager (Realtime toggle for Money, Shards, Kills, Deaths, Playtime, Master Scoreboard).
   - Dynamic Dialog Pay System (2-column player list, saved contacts, 3x3 preset amount templates, custom amount input, confirmation dialog, economy transfer).
2. **Slowy Datapack (`datapack/`)**:
   - Custom Pause Screen integration (`slowy:greeting_dialog`).
   - Interactive Settings Category Dialogs (Chat, Notifications, PvP, Visuals, Privacy, General).

---

## Struktur Repositori

```
Slowy/
├── .gitignore
├── README.md
├── build_all.sh                 # Script build plugin & sync datapack ke server
├── plugin/                      # Source code Slowy Plugin (Java 25 / Paper 1.21.x)
│   ├── build.sh
│   ├── src/
│   │   └── main/
│   │       ├── java/com/slowy/
│   │       │   ├── SlowyPlugin.java
│   │       │   ├── ScoreboardSettings.java
│   │       │   ├── ScoreboardSettingsManager.java
│   │       │   ├── CustomScoreboardManager.java
│   │       │   ├── PayListManager.java
│   │       │   └── PayDialogManager.java
│   │       └── resources/
│   │           └── plugin.yml
└── datapack/                    # Datapack Dialogs Slowy SMP
    ├── pack.mcmeta
    └── data/
        ├── minecraft/tags/dialog/pause_screen_additions.json
        └── slowy/dialog/
            ├── greeting_dialog.json
            ├── settings_dialog.json
            ├── settings_scoreboard_dialog.json
            ├── settings_chat_dialog.json
            ├── settings_general_dialog.json
            ├── settings_notifications_dialog.json
            ├── settings_privacy_dialog.json
            ├── settings_pvp_dialog.json
            └── settings_visuals_dialog.json
```

---

## Cara Build & Deploy ke Server

Cukup jalankan script:
```bash
bash build_all.sh
```

Script ini akan otomatis:
1. Mengompilasi `plugin/` menjadi `Slowy.jar` dan memasangnya di folder `server/plugins/`.
2. Menyalin folder `datapack/` ke `server/world/datapacks/slowy/`.
