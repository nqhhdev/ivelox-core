package com.ivelox.core.common.notification;

/** Shared outbound port for sending a plain-text notification (Telegram today). */
public interface NotificationSenderPort {

    void sendMessage(String text);
}
