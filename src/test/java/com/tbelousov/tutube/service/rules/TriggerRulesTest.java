package com.tbelousov.tutube.service.rules;

import com.tbelousov.tutube.entity.*;
import com.tbelousov.tutube.repository.UserActionRepository;
import com.tbelousov.tutube.service.rules.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TriggerRulesTest {

    private User kindUser;
    private User aggressiveUser;

    @Mock
    private UserActionRepository actionRepo;

    @BeforeEach
    void setUp() {
        kindUser = User.builder()
                .id(1L)
                .name("Alice")
                .timezone("UTC")
                .toneProfile(User.ToneProfile.KIND)
                .mode(User.NotificationMode.RULES_ONLY)
                .build();

        aggressiveUser = User.builder()
                .id(2L)
                .name("Bob")
                .timezone("UTC")
                .toneProfile(User.ToneProfile.PASSIVE_AGGRESSIVE)
                .mode(User.NotificationMode.RULES_ONLY)
                .build();
    }

    // ============= WeatherBasedRule Tests =============

    @Test
    void weatherBasedRule_shouldTriggerOnRainy() {
        // Given
        var rule = new WeatherBasedRule();
        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .weather("rainy")
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("дождь");
        assertThat(result.get().getTone()).isEqualTo(User.ToneProfile.KIND);
    }

    @Test
    void weatherBasedRule_shouldAdaptToneToPassiveAggressive() {
        // Given
        var rule = new WeatherBasedRule();
        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .weather("cold")
                .build();
        var context = createContext(aggressiveUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("отвратительная");
        assertThat(result.get().getTone()).isEqualTo(User.ToneProfile.PASSIVE_AGGRESSIVE);
    }

    @Test
    void weatherBasedRule_shouldNotTriggerOnGoodWeather() {
        // Given
        var rule = new WeatherBasedRule();
        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .weather("sunny")
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void weatherBasedRule_shouldNotTriggerIfRecentlyTriggered() {
        // Given
        var rule = new WeatherBasedRule();
        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .weather("rainy")
                .build();
        var context = createContext(
                kindUser,
                Collections.emptyList(),
                Set.of("WEATHER_BASED")
        );

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= AlwaysSkipsIntroRule Tests =============

    @Test
    void alwaysSkipsIntroRule_shouldTriggerWhenSkipping80Percent() {
        // Given
        var rule = new AlwaysSkipsIntroRule();
        var channelId = 10L;

        // 5 просмотров на канале, 4 из них с пропуском интро
        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).introSkipSec(15).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).introSkipSec(20).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).introSkipSec(12).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).introSkipSec(5).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).introSkipSec(18).build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .channelId(channelId)
                .introSkipSec(16)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("интро");
    }

    @Test
    void alwaysSkipsIntroRule_shouldNotTriggerWithLessThan3Samples() {
        // Given
        var rule = new AlwaysSkipsIntroRule();
        var channelId = 10L;

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).introSkipSec(15).build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .channelId(channelId)
                .introSkipSec(16)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= AntiFilterBubbleRule Tests =============

    @Test
    void antiFilterBubbleRule_shouldTriggerOn3OppositeTopicFlips() {
        // Given
        var rule = new AntiFilterBubbleRule();

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("vegan").build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("keto").build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("vegan").build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("keto").build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("vegan").build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("keto")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("широтой взглядов");
    }

    @Test
    void antiFilterBubbleRule_shouldNotTriggerWithoutOpposites() {
        // Given
        var rule = new AntiFilterBubbleRule();

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("cooking").build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("travel").build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("cooking")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= DonationLoyaltyRule Tests =============

    @Test
    void donationLoyaltyRule_shouldTriggerWhenWatchingDonatedChannel() {
        // Given
        var rule = new DonationLoyaltyRule();
        var channelId = 10L;

        var recentActions = List.of(
                createAction(UserAction.ActionType.DONATE).channelId(channelId).donationAmount(5.0).build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .channelId(channelId)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("за поддержку");
    }

    @Test
    void donationLoyaltyRule_shouldNotTriggerForNonDonatedChannel() {
        // Given
        var rule = new DonationLoyaltyRule();

        var recentActions = List.of(
                createAction(UserAction.ActionType.DONATE).channelId(20L).donationAmount(5.0).build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .channelId(10L)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= DonationWithoutLikeRule Tests =============

    @Test
    void donationWithoutLikeRule_shouldTriggerWhenNoLikesOnChannelVideos() {
        // Given
        var rule = new DonationWithoutLikeRule();
        var channelId = 10L;

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).videoId(100L).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).videoId(101L).build()
                // Нет лайков на эти видео
        );

        var action = createAction(UserAction.ActionType.DONATE)
                .channelId(channelId)
                .donationAmount(10.0)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("лайк");
    }

    @Test
    void donationWithoutLikeRule_shouldNotTriggerWhenVideosAreLiked() {
        // Given
        var rule = new DonationWithoutLikeRule();
        var channelId = 10L;

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId).videoId(100L).build(),
                createAction(UserAction.ActionType.LIKE_VIDEO).videoId(100L).build()
        );

        var action = createAction(UserAction.ActionType.DONATE)
                .channelId(channelId)
                .donationAmount(10.0)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= GrammarHelpRule Tests =============

    @Test
    void grammarHelpRule_shouldTriggerOnManyTypos() {
        // Given
        var rule = new GrammarHelpRule();

        var recentActions = List.of(
                createAction(UserAction.ActionType.COMMENT).typosCount(30).build(),
                createAction(UserAction.ActionType.COMMENT).typosCount(40).build()
        );

        var action = createAction(UserAction.ActionType.COMMENT)
                .typosCount(12)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("опечатки");
    }

    @Test
    void grammarHelpRule_shouldNotTriggerOnFewTypos() {
        // Given
        var rule = new GrammarHelpRule();

        var recentActions = List.of(
                createAction(UserAction.ActionType.COMMENT).typosCount(30).build(),
                createAction(UserAction.ActionType.COMMENT).typosCount(40).build()
        );

        var action = createAction(UserAction.ActionType.COMMENT)
                .typosCount(3)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= InconsistentEngagementRule Tests =============

    @Test
    void inconsistentEngagementRule_shouldTriggerWhenCommentLikedButNotVideo() {
        // Given
        var rule = new InconsistentEngagementRule();
        var videoId = 100L;
        var yesterday = Instant.now().minus(12, ChronoUnit.HOURS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.LIKE_COMMENT)
                        .videoId(videoId)
                        .commentId(500L)
                        .timestamp(yesterday)
                        .build()
                // Нет LIKE_VIDEO для этого videoId
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO).build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("лайкнули комментарий");
    }

    @Test
    void inconsistentEngagementRule_shouldNotTriggerWhenVideoAlsoLiked() {
        // Given
        var rule = new InconsistentEngagementRule();
        var videoId = 100L;
        var yesterday = Instant.now().minus(12, ChronoUnit.HOURS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.LIKE_COMMENT)
                        .videoId(videoId)
                        .commentId(500L)
                        .timestamp(yesterday)
                        .build(),
                createAction(UserAction.ActionType.LIKE_VIDEO)
                        .videoId(videoId)
                        .timestamp(yesterday)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO).build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= LocationBasedRule Tests =============

    @Test
    void locationBasedRule_shouldTriggerOnLocationChange() {
        // Given
        var rule = new LocationBasedRule();
        var pastTime = Instant.now().minus(2, ChronoUnit.HOURS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .location("Moscow")
                        .timestamp(pastTime)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .location("Berlin")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("Новое место");
    }

    @Test
    void locationBasedRule_shouldNotTriggerOnSameLocation() {
        // Given
        var rule = new LocationBasedRule();
        var pastTime = Instant.now().minus(2, ChronoUnit.HOURS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .location("Moscow")
                        .timestamp(pastTime)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .location("Moscow")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= MandelaEffectRule Tests =============

    @Test
    void mandelaEffectRule_shouldTriggerOnSearchingPattern() {
        // Given
        var rule = new MandelaEffectRule();
        var topic = "history";
        var recent = Instant.now().minus(1, ChronoUnit.HOURS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic(topic)
                        .seeksCount(5)
                        .watchDurationSec(100)
                        .timestamp(recent)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic(topic)
                        .seeksCount(6)
                        .watchDurationSec(120)
                        .timestamp(recent)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic(topic)
                        .seeksCount(4)
                        .watchDurationSec(80)
                        .timestamp(recent)
                        .build(),
                createAction(UserAction.ActionType.COMMENT)
                        .timestamp(recent)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic(topic)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("Манделы");
    }

    // ============= MeteoSensitivityRule Tests =============

    @Test
    void meteoSensitivityRule_shouldTriggerOnWeatherChangeWithRelax() {
        // Given
        var rule = new MeteoSensitivityRule();
        var zone = ZoneId.of("UTC");
        var now = Instant.now();

        // День 1: погода менялась, смотрел relax
        var day1 = now.minus(10, ChronoUnit.DAYS);
        var day1Morning = day1.atZone(zone).withHour(9).toInstant();
        var day1Afternoon = day1.atZone(zone).withHour(14).toInstant();

        // День 2: погода менялась, смотрел relax
        var day2 = now.minus(5, ChronoUnit.DAYS);
        var day2Morning = day2.atZone(zone).withHour(10).toInstant();
        var day2Evening = day2.atZone(zone).withHour(19).toInstant();

        // День 3: погода менялась, смотрел relax
        var day3 = now.minus(1, ChronoUnit.DAYS);
        var day3Morning = day3.atZone(zone).withHour(8).toInstant();
        var day3Afternoon = day3.atZone(zone).withHour(15).toInstant();

        var recentActions = List.of(
                // День 1: утром sunny, потом сменилась на rainy, смотрел relax
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .weather("sunny")
                        .timestamp(day1Morning)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("relax")
                        .weather("rainy")
                        .timestamp(day1Afternoon)
                        .build(),

                // День 2: утром cold, потом сменилась на warm, смотрел relax
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .weather("cold")
                        .timestamp(day2Morning)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("relax")
                        .weather("warm")
                        .timestamp(day2Evening)
                        .build(),

                // День 3: утром foggy, потом сменилась на clear, смотрел relax
                createAction(UserAction.ActionType.COMMENT)
                        .weather("foggy")
                        .timestamp(day3Morning)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("relax")
                        .weather("clear")
                        .timestamp(day3Afternoon)
                        .build()
        );

        // Сегодня тоже погода меняется и смотрит relax
        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("relax")
                .weather("snowy")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("Погода");
    }

    @Test
    void meteoSensitivityRule_shouldNotTriggerWithoutRelaxTopic() {
        // Given
        var rule = new MeteoSensitivityRule();
        var zone = ZoneId.of("UTC");
        var now = Instant.now();

        var yesterday = now.minus(1, ChronoUnit.DAYS);
        var yesterdayMorning = yesterday.atZone(zone).withHour(9).toInstant();
        var yesterdayAfternoon = yesterday.atZone(zone).withHour(14).toInstant();

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .weather("sunny")
                        .timestamp(yesterdayMorning)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("cooking") // НЕ relax
                        .weather("rainy")
                        .timestamp(yesterdayAfternoon)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("cooking")
                .weather("cold")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void meteoSensitivityRule_shouldNotTriggerWithoutWeatherChange() {
        // Given
        var rule = new MeteoSensitivityRule();
        var zone = ZoneId.of("UTC");
        var now = Instant.now();

        // 3 дня, но погода НЕ менялась
        var day1 = now.minus(10, ChronoUnit.DAYS).atZone(zone).withHour(14).toInstant();
        var day2 = now.minus(5, ChronoUnit.DAYS).atZone(zone).withHour(14).toInstant();
        var day3 = now.minus(1, ChronoUnit.DAYS).atZone(zone).withHour(14).toInstant();

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("relax")
                        .weather("sunny") // всегда sunny
                        .timestamp(day1)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("relax")
                        .weather("sunny")
                        .timestamp(day2)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("relax")
                        .weather("sunny")
                        .timestamp(day3)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("relax")
                .weather("sunny")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void meteoSensitivityRule_shouldNotTriggerWithOnlyTwoDays() {
        // Given
        var rule = new MeteoSensitivityRule();
        var zone = ZoneId.of("UTC");
        var now = Instant.now();

        // Только 2 дня (нужно 3)
        var day1 = now.minus(5, ChronoUnit.DAYS);
        var day1Morning = day1.atZone(zone).withHour(9).toInstant();
        var day1Afternoon = day1.atZone(zone).withHour(14).toInstant();

        var day2 = now.minus(1, ChronoUnit.DAYS);
        var day2Morning = day2.atZone(zone).withHour(9).toInstant();
        var day2Afternoon = day2.atZone(zone).withHour(14).toInstant();

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .weather("sunny")
                        .timestamp(day1Morning)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("relax")
                        .weather("rainy")
                        .timestamp(day1Afternoon)
                        .build(),

                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .weather("cold")
                        .timestamp(day2Morning)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("relax")
                        .weather("warm")
                        .timestamp(day2Afternoon)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("relax")
                .weather("snowy")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= MilestoneRule Tests =============

    @Test
    void milestoneRule_shouldTriggerOn9Videos() {
        // Given
        var rule = new MilestoneRule();
        var topic = "java";

        var recentActions = new ArrayList<UserAction>();
        for (int i = 0; i < 8; i++) {
            recentActions.add(createAction(UserAction.ActionType.VIEW_VIDEO)
                    .videoTopic(topic)
                    .build());
        }

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic(topic)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("9 видео");
        assertThat(result.get().getMessage()).contains(topic);
    }

    @Test
    void milestoneRule_shouldNotTriggerOn8Videos() {
        // Given
        var rule = new MilestoneRule();
        var topic = "java";

        var recentActions = new ArrayList<UserAction>();
        for (int i = 0; i < 7; i++) {
            recentActions.add(createAction(UserAction.ActionType.VIEW_VIDEO)
                    .videoTopic(topic)
                    .build());
        }

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic(topic)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= NegativeSpiralRule Tests =============

    @Test
    void negativeSpiralRule_shouldTriggerOnMostlyNegativeComments() {
        // Given
        var rule = new NegativeSpiralRule();
        var recent = Instant.now().minus(3, ChronoUnit.DAYS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.COMMENT).sentimentScore(-50).timestamp(recent).build(),
                createAction(UserAction.ActionType.COMMENT).sentimentScore(-60).timestamp(recent).build(),
                createAction(UserAction.ActionType.COMMENT).sentimentScore(-40).timestamp(recent).build(),
                createAction(UserAction.ActionType.COMMENT).sentimentScore(-70).timestamp(recent).build(),
                createAction(UserAction.ActionType.COMMENT).sentimentScore(10).timestamp(recent).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("politics").timestamp(recent).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("conflict").timestamp(recent).build()
        );

        var action = createAction(UserAction.ActionType.COMMENT).build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("паузу");
    }

    // ============= NightAltruistRule Tests =============

    @Test
    void nightAltruistRule_shouldTriggerOnNightDonation() {
        // Given
        var rule = new NightAltruistRule();

        // 3 AM donation
        var nightTime = Instant.parse("2024-01-15T03:00:00Z");

        var action = createAction(UserAction.ActionType.DONATE)
                .timestamp(nightTime)
                .donationAmount(5.0)
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("Ночью");
    }

    @Test
    void nightAltruistRule_shouldNotTriggerOnDayDonation() {
        // Given
        var rule = new NightAltruistRule();

        // 3 PM donation
        var dayTime = Instant.parse("2024-01-15T15:00:00Z");

        var action = createAction(UserAction.ActionType.DONATE)
                .timestamp(dayTime)
                .donationAmount(5.0)
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= NightOwlRule Tests =============

    @Test
    void nightOwlRule_shouldTriggerOn3NightComments() {
        // Given
        var rule = new NightOwlRule();
        var zone = ZoneId.of("UTC");
        var now = Instant.now();

        var night1 = now.minus(3, ChronoUnit.DAYS).atZone(zone)
                .withHour(1).withMinute(0).withSecond(0).toInstant();
        var night2 = now.minus(2, ChronoUnit.DAYS).atZone(zone)
                .withHour(2).withMinute(0).withSecond(0).toInstant();
        var night3 = now.minus(1, ChronoUnit.DAYS).atZone(zone)
                .withHour(3).withMinute(0).withSecond(0).toInstant();

        var recentActions = List.of(
                createAction(UserAction.ActionType.COMMENT).timestamp(night1).build(),
                createAction(UserAction.ActionType.COMMENT).timestamp(night2).build(),
                createAction(UserAction.ActionType.COMMENT).timestamp(night3).build()
        );

        var action = createAction(UserAction.ActionType.COMMENT)
                .timestamp(night3)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("сова");
    }

    // ============= PaydayDonationRule Tests =============

    @Test
    void paydayDonationRule_shouldTriggerOnPaydayWithoutDonation() {
        // Given
        var rule = new PaydayDonationRule();
        var zone = ZoneId.of("UTC");
        var now = Instant.now();

        // Donates on 15th of month
        var day15_month1 = now.minus(92, ChronoUnit.DAYS).atZone(zone)
                .withDayOfMonth(15).withHour(12).withMinute(0).toInstant();
        var day15_month2 = now.minus(61, ChronoUnit.DAYS).atZone(zone)
                .withDayOfMonth(15).withHour(12).withMinute(0).toInstant();
        var day15_month3 = now.minus(31, ChronoUnit.DAYS).atZone(zone)
                .withDayOfMonth(15).withHour(12).withMinute(0).toInstant();
        var day15_today = now.atZone(zone)
                .withDayOfMonth(15).withHour(12).withMinute(0).toInstant();

        var recentActions = List.of(
                createAction(UserAction.ActionType.DONATE).timestamp(day15_month1).build(),
                createAction(UserAction.ActionType.DONATE).timestamp(day15_month2).build(),
                createAction(UserAction.ActionType.DONATE).timestamp(day15_month3).build()
        );

        // Now it's 15th again, but no donation yet
        var action = createAction(UserAction.ActionType.VIEW_VIDEO).timestamp(day15_today).build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("поддерживаете");
    }

    // ============= PreflightPreloadRule Tests =============

    @Test
    void preflightPreloadRule_shouldTriggerOnFrequentTravelOnMobile() {
        // Given
        var rule = new PreflightPreloadRule();
        var recent = Instant.now().minus(1, ChronoUnit.HOURS);

        var recentActions = new ArrayList<UserAction>();
        // 6 different locations
        for (int i = 0; i < 6; i++) {
            recentActions.add(createAction(UserAction.ActionType.VIEW_VIDEO)
                    .location("City" + i)
                    .timestamp(recent)
                    .build());
        }

        // Recent location change
        recentActions.add(createAction(UserAction.ActionType.VIEW_VIDEO)
                .location("CityA")
                .timestamp(Instant.now().minus(30, ChronoUnit.MINUTES))
                .build());

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .location("CityB")
                .deviceType(UserAction.DeviceType.MOBILE)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("дорогу");
    }

    // ============= RepeatedVideoWatchRule Tests =============

    @Test
    void repeatedVideoWatchRule_shouldTriggerOnThreePlays() {
        // Given
        var rule = new RepeatedVideoWatchRule();
        var videoId = 100L;
        var today = Instant.now();

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoId(videoId)
                        .timestamp(today.minus(2, ChronoUnit.HOURS))
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoId(videoId)
                        .timestamp(today.minus(1, ChronoUnit.HOURS))
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoId(videoId)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("фоне");
    }

    // ============= ShadowsOfPastRule Tests =============

    @Test
    void shadowsOfPastRule_shouldTriggerOnReturnToOldTopic() {
        // Given
        var rule = new ShadowsOfPastRule();
        var longAgo = Instant.now().minus(100, ChronoUnit.DAYS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("guitar_lessons")
                        .videoProgress(90)
                        .timestamp(longAgo)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("rock_history")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("Возвращаетесь");
    }

    // ============= SubscribedButInactiveRule Tests =============

    @Test
    void subscribedButInactiveRule_shouldTriggerOn21DaysInactive() {
        // Given
        var rule = new SubscribedButInactiveRule();
        var channelId = 10L;
        var longAgo = Instant.now().minus(25, ChronoUnit.DAYS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.SUBSCRIBE)
                        .channelId(channelId)
                        .timestamp(longAgo)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .channelId(channelId)
                        .timestamp(longAgo)
                        .build()
                // Нет просмотров последние 21 день
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO).build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("Давно не заглядывали");
    }

    @Test
    void subscribedButInactiveRule_shouldNotTriggerWithRecentActivity() {
        // Given
        var rule = new SubscribedButInactiveRule();
        var channelId = 10L;
        var recent = Instant.now().minus(5, ChronoUnit.DAYS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.SUBSCRIBE)
                        .channelId(channelId)
                        .timestamp(recent)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .channelId(channelId)
                        .timestamp(recent)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO).build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= ThreeDaysUnfinishedTopicRule Tests =============

    @Test
    void threeDaysUnfinishedTopicRule_shouldTriggerOnThreeDaysUnfinished() {
        // Given
        var rule = new ThreeDaysUnfinishedTopicRule();
        var topic = "quantum_physics";

        var day1 = Instant.now().minus(3, ChronoUnit.DAYS);
        var day2 = Instant.now().minus(2, ChronoUnit.DAYS);
        var day3 = Instant.now().minus(1, ChronoUnit.DAYS);
        var day4 = Instant.now();

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic(topic)
                        .videoProgress(30)
                        .timestamp(day1)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic(topic)
                        .videoProgress(45)
                        .timestamp(day2)
                        .build(),
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic(topic)
                        .videoProgress(50)
                        .timestamp(day3)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic(topic)
                .videoProgress(55)
                .timestamp(day4)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("длинновато");
        assertThat(result.get().getMessage()).contains(topic);
    }

    @Test
    void threeDaysUnfinishedTopicRule_shouldNotTriggerOnFinishedVideos() {
        // Given
        var rule = new ThreeDaysUnfinishedTopicRule();
        var topic = "cooking";

        var day1 = Instant.now().minus(2, ChronoUnit.DAYS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic(topic)
                        .videoProgress(95)
                        .timestamp(day1)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic(topic)
                .videoProgress(90)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= UnfinishedVideoRule Tests =============

    @Test
    void unfinishedVideoRule_shouldTriggerInEvening() {
        // Given
        var rule = new UnfinishedVideoRule();

        // 8 PM
        var eveningTime = Instant.parse("2024-01-15T20:00:00Z");

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoProgress(60)
                .timestamp(eveningTime)
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("досмотреть");
    }

    @Test
    void unfinishedVideoRule_shouldNotTriggerWhenVideoFinished() {
        // Given
        var rule = new UnfinishedVideoRule();

        var eveningTime = Instant.parse("2024-01-15T20:00:00Z");

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoProgress(95)
                .timestamp(eveningTime)
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void unfinishedVideoRule_shouldNotTriggerInMorning() {
        // Given
        var rule = new UnfinishedVideoRule();

        // 10 AM
        var morningTime = Instant.parse("2024-01-15T10:00:00Z");

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoProgress(60)
                .timestamp(morningTime)
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= UnspokenGratitudeRule Tests =============

    @Test
    void unspokenGratitudeRule_shouldTriggerOnHighFinishRateWithoutLikes() {
        // Given
        var rule = new UnspokenGratitudeRule();
        var channelId = 10L;
        var recent = Instant.now().minus(10, ChronoUnit.DAYS);

        var recentActions = new ArrayList<UserAction>();
        // 5 видео досмотрены до конца
        for (int i = 0; i < 5; i++) {
            recentActions.add(createAction(UserAction.ActionType.VIEW_VIDEO)
                    .channelId(channelId)
                    .videoProgress(98)
                    .timestamp(recent)
                    .build());
        }
        // Нет лайков и комментариев

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .channelId(channelId)
                .videoProgress(97)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("тайный поклонник");
    }

    @Test
    void unspokenGratitudeRule_shouldNotTriggerWithLikes() {
        // Given
        var rule = new UnspokenGratitudeRule();
        var channelId = 10L;
        var recent = Instant.now().minus(10, ChronoUnit.DAYS);

        var recentActions = new ArrayList<UserAction>();
        for (int i = 0; i < 5; i++) {
            recentActions.add(createAction(UserAction.ActionType.VIEW_VIDEO)
                    .channelId(channelId)
                    .videoProgress(98)
                    .timestamp(recent)
                    .build());
        }
        // Есть лайки
        recentActions.add(createAction(UserAction.ActionType.LIKE_VIDEO)
                .channelId(channelId)
                .timestamp(recent)
                .build());
        recentActions.add(createAction(UserAction.ActionType.LIKE_VIDEO)
                .channelId(channelId)
                .timestamp(recent)
                .build());

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .channelId(channelId)
                .videoProgress(97)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= YearAgoWatchRule Tests =============

    @Test
    void yearAgoWatchRule_shouldTriggerOnYearAnniversary() {
        // Given
        var rule = new YearAgoWatchRule();

        var oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("cooking")
                        .timestamp(oneYearAgo)
                        .build(),
                createAction(UserAction.ActionType.LIKE_VIDEO)
                        .timestamp(oneYearAgo)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("cooking")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).contains("год назад");
    }

    @Test
    void yearAgoWatchRule_shouldNotTriggerWithoutYearAgoActivity() {
        // Given
        var rule = new YearAgoWatchRule();

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic("cooking")
                        .timestamp(Instant.now().minus(30, ChronoUnit.DAYS))
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("cooking")
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    // ============= Edge Cases & Integration Tests =============

    @Test
    void allRules_shouldRespectCooldown() {
        // Given
        var rule = new WeatherBasedRule();
        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .weather("rainy")
                .build();
        var context = createContext(
                kindUser,
                Collections.emptyList(),
                Set.of("WEATHER_BASED")
        );

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void allRules_shouldHandleNullFields() {
        // Given - создаём правила которые требуют nullable поля
        var weatherRule = new WeatherBasedRule();
        var locationRule = new LocationBasedRule();

        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .weather(null)
                .location(null)
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When & Then - не должно быть NPE
        assertThat(weatherRule.evaluate(action, context)).isEmpty();
        assertThat(locationRule.evaluate(action, context)).isEmpty();
    }

    @Test
    void allRules_shouldHandleEmptyRecentActions() {
        // Given
        var rule = new MilestoneRule();
        var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                .videoTopic("java")
                .build();
        var context = createContext(kindUser, Collections.emptyList());

        // When
        var result = rule.evaluate(action, context);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void milestoneRule_shouldTriggerOnMultipleMilestones() {
        // Given
        var rule = new MilestoneRule();
        var topic = "java";

        // Test all milestone numbers
        int[] milestones = {9, 49, 99, 499, 999};

        for (int milestone : milestones) {
            var recentActions = new ArrayList<UserAction>();
            for (int i = 0; i < milestone - 1; i++) {
                recentActions.add(createAction(UserAction.ActionType.VIEW_VIDEO)
                        .videoTopic(topic)
                        .build());
            }

            var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                    .videoTopic(topic)
                    .build();
            var context = createContext(kindUser, recentActions);

            // When
            var result = rule.evaluate(action, context);

            // Then
            assertThat(result)
                    .as("Should trigger on milestone %d", milestone)
                    .isPresent();
            assertThat(result.get().getMessage())
                    .contains(String.valueOf(milestone));
        }
    }

    @Test
    void donationWithoutLikeRule_shouldHandleMultipleChannels() {
        // Given
        var rule = new DonationWithoutLikeRule();
        var channelId1 = 10L;
        var channelId2 = 20L;

        var recentActions = List.of(
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId1).videoId(100L).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).channelId(channelId2).videoId(200L).build(),
                createAction(UserAction.ActionType.LIKE_VIDEO).videoId(200L).build() // liked channel2
        );

        var action = createAction(UserAction.ActionType.DONATE)
                .channelId(channelId1)
                .donationAmount(10.0)
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then - should trigger because channel1 videos are not liked
        assertThat(result).isPresent();
    }

    @Test
    void antiFilterBubbleRule_shouldHandleAllOppositePairs() {
        // Given
        var rule = new AntiFilterBubbleRule();

        var pairs = List.of(
                List.of("vegan", "keto"),
                List.of("android", "ios"),
                List.of("left_politics", "right_politics")
        );

        for (var pair : pairs) {
            var recentActions = List.of(
                    createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic(pair.get(0)).build(),
                    createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic(pair.get(1)).build(),
                    createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic(pair.get(0)).build(),
                    createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic(pair.get(1)).build()
            );

            var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                    .videoTopic(pair.get(0))
                    .build();
            var context = createContext(kindUser, recentActions);

            // When
            var result = rule.evaluate(action, context);

            // Then
            assertThat(result)
                    .as("Should trigger for opposite pair: %s <-> %s", pair.get(0), pair.get(1))
                    .isPresent();
        }
    }

    @Test
    void nightOwlRule_shouldRequireDistinctNights() {
        // Given
        var rule = new NightOwlRule();

        // All comments same night
        var sameNight = Instant.parse("2024-01-15T02:00:00Z");

        var recentActions = List.of(
                createAction(UserAction.ActionType.COMMENT).timestamp(sameNight).build(),
                createAction(UserAction.ActionType.COMMENT).timestamp(sameNight.plus(1, ChronoUnit.HOURS)).build()
        );

        var action = createAction(UserAction.ActionType.COMMENT)
                .timestamp(sameNight.plus(2, ChronoUnit.HOURS))
                .build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then - should not trigger because all on same night
        assertThat(result).isEmpty();
    }

    @Test
    void shadowsOfPastRule_shouldHandleAllPassivePairs() {
        // Given
        var rule = new ShadowsOfPastRule();
        var longAgo = Instant.now().minus(100, ChronoUnit.DAYS);

        var pairs = Map.of(
                "guitar_lessons", "rock_history",
                "coding_tutorials", "tech_history",
                "fitness_workouts", "sports_history"
        );

        for (var entry : pairs.entrySet()) {
            var recentActions = List.of(
                    createAction(UserAction.ActionType.VIEW_VIDEO)
                            .videoTopic(entry.getKey())
                            .videoProgress(90)
                            .timestamp(longAgo)
                            .build()
            );

            var action = createAction(UserAction.ActionType.VIEW_VIDEO)
                    .videoTopic(entry.getValue())
                    .build();
            var context = createContext(kindUser, recentActions);

            // When
            var result = rule.evaluate(action, context);

            // Then
            assertThat(result)
                    .as("Should trigger for pair: %s -> %s", entry.getKey(), entry.getValue())
                    .isPresent();
        }
    }

    @Test
    void negativeSpiralRule_shouldHandleMixedSentiments() {
        // Given
        var rule = new NegativeSpiralRule();
        var recent = Instant.now().minus(3, ChronoUnit.DAYS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.COMMENT).sentimentScore(-50).timestamp(recent).build(),
                createAction(UserAction.ActionType.COMMENT).sentimentScore(50).timestamp(recent).build(),
                createAction(UserAction.ActionType.COMMENT).sentimentScore(60).timestamp(recent).build(),
                createAction(UserAction.ActionType.COMMENT).sentimentScore(-20).timestamp(recent).build(),
                createAction(UserAction.ActionType.COMMENT).sentimentScore(70).timestamp(recent).build(),
                createAction(UserAction.ActionType.VIEW_VIDEO).videoTopic("politics").timestamp(recent).build()
        );

        var action = createAction(UserAction.ActionType.COMMENT).build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then - should not trigger, mostly positive
        assertThat(result).isEmpty();
    }

    @Test
    void inconsistentEngagementRule_shouldIgnoreOldLikes() {
        // Given
        var rule = new InconsistentEngagementRule();
        var videoId = 100L;
        var twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);

        var recentActions = List.of(
                createAction(UserAction.ActionType.LIKE_COMMENT)
                        .videoId(videoId)
                        .commentId(500L)
                        .timestamp(twoDaysAgo)
                        .build()
        );

        var action = createAction(UserAction.ActionType.VIEW_VIDEO).build();
        var context = createContext(kindUser, recentActions);

        // When
        var result = rule.evaluate(action, context);

        // Then - should not trigger, like is too old (>24h)
        assertThat(result).isEmpty();
    }

    // ============= Helper Methods =============

    private UserAction.UserActionBuilder createAction(UserAction.ActionType type) {
        return UserAction.builder()
                .id(1L)
                .userId(1L)
                .actionType(type)
                .timestamp(Instant.now())
                .deviceType(UserAction.DeviceType.DESKTOP)
                .videoId(100L)
                .channelId(10L);
    }

    private RuleContext createContext(User user, List<UserAction> recentActions) {
        return createContext(user, recentActions, Collections.emptySet());
    }

    private RuleContext createContext(User user, List<UserAction> recentActions, Set<String> recentTriggers) {
        return RuleContext.builder()
                .user(user)
                .recentActions(recentActions)
                .recentlyTriggeredRules(recentTriggers)
                .build();
    }
}