package com.rtcdelivery.foodcatalog.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.foodcatalog.dto.event.EventEnvelope;
import com.rtcdelivery.foodcatalog.dto.event.TranslationResultPayload;
import com.rtcdelivery.foodcatalog.repository.FoodRepository;
import com.rtcdelivery.foodcatalog.repository.RestaurantRepository;
import com.rtcdelivery.foodcatalog.service.InboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResultConsumer {

    private final InboxService inboxService;
    private final RestaurantRepository restaurantRepository;
    private final FoodRepository foodRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "translation-results", groupId = "food-catalog-group")
    @Transactional
    public void consume(String message) {
        try {
            EventEnvelope<TranslationResultPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<TranslationResultPayload>>() {}
            );

            if (!inboxService.markProcessed(envelope.eventId(), "food-catalog-group", envelope.eventType())) {
                log.info("Duplicate translation result event ignored: eventId={}", envelope.eventId());
                return;
            }

            TranslationResultPayload payload = envelope.payload();
            if (payload == null || payload.entries() == null) {
                log.warn("Empty translation result payload: eventId={}", envelope.eventId());
                return;
            }

            String targetLocale = payload.targetLocale();

            for (TranslationResultPayload.ResultEntry entry : payload.entries()) {
                if ("RESTAURANT".equalsIgnoreCase(entry.targetType())) {
                    restaurantRepository.findById(entry.targetId()).ifPresentOrElse(
                            restaurant -> {
                                restaurant.putTranslation(targetLocale, entry.name(), entry.description());
                                log.info("Updated restaurant translation: restaurantId={}, locale={}",
                                        entry.targetId(), targetLocale);
                            },
                            () -> log.warn("Restaurant not found for translation result: id={}", entry.targetId())
                    );
                } else if ("MENU".equalsIgnoreCase(entry.targetType())) {
                    foodRepository.findById(entry.targetId()).ifPresentOrElse(
                            food -> {
                                food.putTranslation(targetLocale, entry.name(), entry.description());
                                log.info("Updated food translation: foodId={}, locale={}",
                                        entry.targetId(), targetLocale);
                            },
                            () -> log.warn("Food not found for translation result: id={}", entry.targetId())
                    );
                } else {
                    log.warn("Unknown targetType in translation result: {}", entry.targetType());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process translation result message: {}", message, e);
            throw new RuntimeException("Failed to process translation result", e);
        }
    }
}
