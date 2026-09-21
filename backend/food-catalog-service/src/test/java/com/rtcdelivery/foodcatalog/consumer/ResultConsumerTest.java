package com.rtcdelivery.foodcatalog.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.foodcatalog.domain.Category;
import com.rtcdelivery.foodcatalog.domain.Food;
import com.rtcdelivery.foodcatalog.domain.Restaurant;
import com.rtcdelivery.foodcatalog.dto.event.EventEnvelope;
import com.rtcdelivery.foodcatalog.dto.event.TranslationResultPayload;
import com.rtcdelivery.foodcatalog.repository.FoodRepository;
import com.rtcdelivery.foodcatalog.repository.RestaurantRepository;
import com.rtcdelivery.foodcatalog.service.InboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResultConsumerTest {

    @Mock
    private InboxService inboxService;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private FoodRepository foodRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ResultConsumer resultConsumer;

    private Restaurant restaurant;
    private Food food;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder()
                .id(1L)
                .ownerId(100L)
                .category(Category.builder().id(1L).code("korean").name("한식").build())
                .name("서울 김치찌개")
                .description("30년 전통")
                .isActive(true)
                .build();

        food = Food.builder()
                .id(10L)
                .restaurant(restaurant)
                .name("김치찌개")
                .description("묵은지 찌개")
                .price(9000)
                .build();
    }

    @Test
    @DisplayName("consume_중복_이벤트는_무시된다")
    void consume_duplicateEvent_isIgnored() throws Exception {
        TranslationResultPayload payload = new TranslationResultPayload(
                "ja", "ko", List.of(new TranslationResultPayload.ResultEntry("RESTAURANT", 1L, "名", "説明"))
        );
        EventEnvelope<TranslationResultPayload> envelope = EventEnvelope.of(
                "event-dup", "TRANSLATION_COMPLETED", "RESTAURANT", "1", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed("event-dup", "food-catalog-group", "TRANSLATION_COMPLETED"))
                .willReturn(false);

        resultConsumer.consume(json);

        verify(restaurantRepository, never()).findById(any());
        verify(foodRepository, never()).findById(any());
    }

    @Test
    @DisplayName("consume_음식점_번역을_정상_반영한다")
    void consume_updatesRestaurantTranslation() throws Exception {
        TranslationResultPayload payload = new TranslationResultPayload(
                "ja", "ko", List.of(
                        new TranslationResultPayload.ResultEntry("RESTAURANT", 1L, "ソウルキムチチゲ", "30年伝統")
                )
        );
        EventEnvelope<TranslationResultPayload> envelope = EventEnvelope.of(
                "event-1", "TRANSLATION_COMPLETED", "RESTAURANT", "1", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed("event-1", "food-catalog-group", "TRANSLATION_COMPLETED"))
                .willReturn(true);
        given(restaurantRepository.findById(1L)).willReturn(Optional.of(restaurant));

        resultConsumer.consume(json);

        assertThat(restaurant.resolveName("ja")).isEqualTo("ソウルキムチチゲ");
        assertThat(restaurant.resolveDescription("ja")).isEqualTo("30年伝統");
    }

    @Test
    @DisplayName("consume_메뉴_번역을_정상_반영한다")
    void consume_updatesFoodTranslation() throws Exception {
        TranslationResultPayload payload = new TranslationResultPayload(
                "ja", "ko", List.of(
                        new TranslationResultPayload.ResultEntry("MENU", 10L, "キムチチゲ", "熟成キムチチゲ")
                )
        );
        EventEnvelope<TranslationResultPayload> envelope = EventEnvelope.of(
                "event-2", "TRANSLATION_COMPLETED", "RESTAURANT", "1", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed("event-2", "food-catalog-group", "TRANSLATION_COMPLETED"))
                .willReturn(true);
        given(foodRepository.findById(10L)).willReturn(Optional.of(food));

        resultConsumer.consume(json);

        assertThat(food.resolveName("ja")).isEqualTo("キムチチゲ");
        assertThat(food.resolveDescription("ja")).isEqualTo("熟成キムチチゲ");
    }

    @Test
    @DisplayName("consume_엔티티가_DB에_없으면_조용히_스킵한다")
    void consume_missingEntity_isSkipped() throws Exception {
        TranslationResultPayload payload = new TranslationResultPayload(
                "ja", "ko", List.of(
                        new TranslationResultPayload.ResultEntry("RESTAURANT", 999L, "名", "説明"),
                        new TranslationResultPayload.ResultEntry("MENU", 888L, "名", "説明"),
                        new TranslationResultPayload.ResultEntry("UNKNOWN", 777L, "名", "説明")
                )
        );
        EventEnvelope<TranslationResultPayload> envelope = EventEnvelope.of(
                "event-3", "TRANSLATION_COMPLETED", "RESTAURANT", "999", payload
        );
        String json = objectMapper.writeValueAsString(envelope);

        given(inboxService.markProcessed("event-3", "food-catalog-group", "TRANSLATION_COMPLETED"))
                .willReturn(true);
        given(restaurantRepository.findById(999L)).willReturn(Optional.empty());
        given(foodRepository.findById(888L)).willReturn(Optional.empty());

        resultConsumer.consume(json);
        // Does not throw exception
    }

    @Test
    @DisplayName("consume_잘못된_JSON이면_예외를_던진다")
    void consume_invalidJson_throwsException() {
        assertThatThrownBy(() -> resultConsumer.consume("invalid-json"))
                .isInstanceOf(RuntimeException.class);
    }
}
