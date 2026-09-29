package com.buildup.material.dto;

import com.buildup.material.entity.MaterialRequestEvent;

import java.time.LocalDateTime;

public record MaterialRequestEventResponse(
        String status,
        String actorName,
        Long actorUserId,
        boolean byHq,
        LocalDateTime at
) {
    public static MaterialRequestEventResponse from(MaterialRequestEvent event) {
        return new MaterialRequestEventResponse(
                event.getStatus(),
                event.getActor(),
                event.getActorUser() == null ? null : event.getActorUser().getId(),
                event.isByHq(),
                event.getAt()
        );
    }
}
