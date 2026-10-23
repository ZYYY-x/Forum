package com.example.api.dto;

public record NotificationCreateRequest(
        String eventId,
        int uid,
        String title,
        String content,
        String type,
        String url
) {
}
