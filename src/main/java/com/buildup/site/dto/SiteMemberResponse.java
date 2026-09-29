package com.buildup.site.dto;

import com.buildup.site.entity.MembershipStatus;
import com.buildup.site.entity.SiteMember;
import com.buildup.site.entity.SiteMemberRole;

import java.time.LocalDateTime;

public record SiteMemberResponse(
        Long membershipId,
        Long userId,
        String name,
        String email,
        SiteMemberRole role,
        MembershipStatus status,
        LocalDateTime joinedAt
) {

    public static SiteMemberResponse from(SiteMember member) {
        return new SiteMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                member.getMemberRole(),
                member.getStatus(),
                member.getJoinedAt()
        );
    }
}
