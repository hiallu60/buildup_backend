package com.buildup.material.entity;

import com.buildup.site.entity.Site;
import com.buildup.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterialRequestTest {

    @Test
    void submitRejectsEmptyItemsEvenWithoutDtoValidation() {
        Site site = mock(Site.class);
        User author = mock(User.class);

        assertThatThrownBy(() -> MaterialRequest.submit(
                site,
                "FOUNDATION",
                "기초 공사",
                null,
                false,
                null,
                author,
                List.of(),
                false,
                LocalDateTime.now()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void transitionKeepsCurrentStatusAndLatestEventConsistent() {
        Site site = mock(Site.class);
        User author = mock(User.class);
        when(author.getName()).thenReturn("요청자");
        MaterialRequest request = MaterialRequest.submit(
                site,
                "FOUNDATION",
                "기초 공사",
                null,
                false,
                null,
                author,
                List.of(MaterialRequestItem.of(
                        "시멘트",
                        new BigDecimal("20.000"),
                        "포"
                )),
                false,
                LocalDateTime.now()
        );

        assertThat(request.hasConsistentStatusHistory()).isTrue();

        request.transition(
                MaterialRequestStatus.APPROVED,
                null,
                author,
                false,
                LocalDateTime.now()
        );

        assertThat(request.getStatus()).isEqualTo(MaterialRequestStatus.APPROVED);
        assertThat(request.getEvents().get(1).getStatus())
                .isEqualTo(MaterialRequestStatus.APPROVED);
        assertThat(request.hasConsistentStatusHistory()).isTrue();
    }
}
