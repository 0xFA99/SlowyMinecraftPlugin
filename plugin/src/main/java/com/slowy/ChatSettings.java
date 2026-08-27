package com.slowy;

public class ChatSettings {
    private boolean publicChat = true;
    private ChatVisibility privateMessages = ChatVisibility.ANYONE;
    private boolean serverChatMessages = true;
    private boolean serverHotbarMessages = true;
    private ChatVisibility deathMessages = ChatVisibility.ANYONE;
    private ChatVisibility advancementMessages = ChatVisibility.FRIENDS;
    private ChatVisibility joinLeaveMessages = ChatVisibility.FRIENDS;

    public ChatSettings() {}

    public boolean isPublicChat() {
        return publicChat;
    }

    public void setPublicChat(boolean publicChat) {
        this.publicChat = publicChat;
    }

    public ChatVisibility getPrivateMessages() {
        return privateMessages;
    }

    public void setPrivateMessages(ChatVisibility privateMessages) {
        this.privateMessages = privateMessages != null ? privateMessages : ChatVisibility.ANYONE;
    }

    public boolean isServerChatMessages() {
        return serverChatMessages;
    }

    public void setServerChatMessages(boolean serverChatMessages) {
        this.serverChatMessages = serverChatMessages;
    }

    public boolean isServerHotbarMessages() {
        return serverHotbarMessages;
    }

    public void setServerHotbarMessages(boolean serverHotbarMessages) {
        this.serverHotbarMessages = serverHotbarMessages;
    }

    public ChatVisibility getDeathMessages() {
        return deathMessages;
    }

    public void setDeathMessages(ChatVisibility deathMessages) {
        this.deathMessages = deathMessages != null ? deathMessages : ChatVisibility.ANYONE;
    }

    public ChatVisibility getAdvancementMessages() {
        return advancementMessages;
    }

    public void setAdvancementMessages(ChatVisibility advancementMessages) {
        this.advancementMessages = advancementMessages != null ? advancementMessages : ChatVisibility.FRIENDS;
    }

    public ChatVisibility getJoinLeaveMessages() {
        return joinLeaveMessages;
    }

    public void setJoinLeaveMessages(ChatVisibility joinLeaveMessages) {
        this.joinLeaveMessages = joinLeaveMessages != null ? joinLeaveMessages : ChatVisibility.FRIENDS;
    }
}
