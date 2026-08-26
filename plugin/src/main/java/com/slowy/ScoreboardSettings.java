package com.slowy;

public class ScoreboardSettings {
    private boolean scoreboardEnabled = true;
    private boolean showMoney = true;
    private boolean showShards = true;
    private boolean showKills = false;
    private boolean showDeaths = false;
    private boolean showPlaytime = true;

    public ScoreboardSettings() {}

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public void setScoreboardEnabled(boolean scoreboardEnabled) {
        this.scoreboardEnabled = scoreboardEnabled;
    }

    public boolean isShowMoney() {
        return showMoney;
    }

    public void setShowMoney(boolean showMoney) {
        this.showMoney = showMoney;
    }

    public boolean isShowShards() {
        return showShards;
    }

    public void setShowShards(boolean showShards) {
        this.showShards = showShards;
    }

    public boolean isShowKills() {
        return showKills;
    }

    public void setShowKills(boolean showKills) {
        this.showKills = showKills;
    }

    public boolean isShowDeaths() {
        return showDeaths;
    }

    public void setShowDeaths(boolean showDeaths) {
        this.showDeaths = showDeaths;
    }

    public boolean isShowPlaytime() {
        return showPlaytime;
    }

    public void setShowPlaytime(boolean showPlaytime) {
        this.showPlaytime = showPlaytime;
    }
}
