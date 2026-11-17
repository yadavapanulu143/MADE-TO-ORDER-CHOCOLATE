package com.choco.notification.domain.notification;

import static com.choco.notification.domain.notification.NotificationTestSupport.LATER;
import static com.choco.notification.domain.notification.NotificationTestSupport.NOTIFICATION_ID;
import static com.choco.notification.domain.notification.NotificationTestSupport.T0;
import static com.choco.notification.domain.notification.NotificationTestSupport.scheduled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("A notification")
class NotificationTest {

    @Nested
    @DisplayName("when scheduled")
    class WhenScheduled {

        @Test
        void starts_pending_with_no_attempts() {
            Notification notification = scheduled();

            assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.attempts()).isZero();
            assertThat(notification.sentAt()).isNull();
            assertThat(notification.channel())
                    .as("the channel is a property of the type, not something a caller chooses")
                    .isEqualTo(NotificationChannel.EMAIL);
        }

        @Test
        void rejects_a_blank_recipient() {
            assertThatThrownBy(() -> Notification.schedule(NOTIFICATION_ID,
                    NotificationType.ORDER_CONFIRMED, RecipientType.CUSTOMER, "  ", "s", "b",
                    "dedupe", "ref", T0))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("recipient");
        }

        @Test
        void rejects_a_blank_dedupe_key() {
            assertThatThrownBy(() -> Notification.schedule(NOTIFICATION_ID,
                    NotificationType.ORDER_CONFIRMED, RecipientType.CUSTOMER, "a@b.com", "s", "b",
                    "", "ref", T0))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("deduplication");
        }
    }

    @Nested
    @DisplayName("delivery outcomes")
    class DeliveryOutcomes {

        @Test
        void marks_sent_and_raises_an_event() {
            Notification notification = scheduled();

            notification.markSent(LATER);

            assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.attempts()).isEqualTo(1);
            assertThat(notification.sentAt()).isEqualTo(LATER);
            assertThat(notification.peekDomainEvents())
                    .extracting(e -> e.eventType())
                    .containsExactly("notification.sent");
        }

        @Test
        void a_repeated_send_is_a_no_op_not_a_second_event() {
            Notification notification = scheduled();
            notification.markSent(LATER);
            notification.pullDomainEvents();

            notification.markSent(LATER);

            assertThat(notification.attempts())
                    .as("a harmless retry must not inflate the attempt count")
                    .isEqualTo(1);
            assertThat(notification.peekDomainEvents())
                    .as("no second sent event for a message that went out once")
                    .isEmpty();
        }

        @Test
        void a_failure_below_the_limit_rests_in_failed() {
            Notification notification = scheduled();

            notification.recordFailure("mail server down", 3, LATER);

            assertThat(notification.status())
                    .as("a failed notification stays visible and resendable, not silently reset")
                    .isEqualTo(NotificationStatus.FAILED);
            assertThat(notification.isResendable()).isTrue();
            assertThat(notification.attempts()).isEqualTo(1);
            assertThat(notification.lastError()).isEqualTo("mail server down");
            assertThat(notification.peekDomainEvents())
                    .as("a retryable failure is not yet something the seller must act on")
                    .isEmpty();
        }

        @Test
        void a_failure_at_the_limit_dead_letters_and_raises_an_event() {
            Notification notification = scheduled();
            notification.recordFailure("first", 3, LATER);
            notification.recordFailure("second", 3, LATER);
            notification.recordFailure("third", 3, LATER);

            assertThat(notification.status()).isEqualTo(NotificationStatus.DEAD_LETTER);
            assertThat(notification.attempts()).isEqualTo(3);
            assertThat(notification.peekDomainEvents())
                    .extracting(e -> e.eventType())
                    .containsExactly("notification.failed");
        }

        @Test
        void a_late_failure_report_does_not_disturb_a_sent_notification() {
            Notification notification = scheduled();
            notification.markSent(LATER);

            notification.recordFailure("a straggler", 3, LATER);

            assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.attempts()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("resending")
    class Resending {

        @Test
        void a_failed_notification_can_be_resent_with_a_fresh_budget() {
            Notification notification = scheduled();
            notification.recordFailure("mail down", 3, LATER);

            assertThat(notification.isResendable()).isTrue();

            notification.resetForResend(LATER);

            assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.attempts())
                    .as("a human decision earns a fresh retry budget")
                    .isZero();
            assertThat(notification.lastError()).isNull();
        }

        @Test
        void a_dead_lettered_notification_can_be_resent() {
            Notification notification = scheduled();
            notification.recordFailure("x", 1, LATER);

            assertThat(notification.status()).isEqualTo(NotificationStatus.DEAD_LETTER);
            assertThat(notification.isResendable()).isTrue();
        }

        @Test
        void a_sent_notification_cannot_be_resent() {
            Notification notification = scheduled();
            notification.markSent(LATER);

            assertThat(notification.isResendable()).isFalse();
            assertThatThrownBy(() -> notification.resetForResend(LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void a_suppressed_notification_cannot_be_resent() {
            Notification notification = scheduled();
            notification.suppress(LATER);

            assertThat(notification.isResendable()).isFalse();
            assertThatThrownBy(() -> notification.resetForResend(LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Test
    @DisplayName("stores a bounded, single-line failure summary")
    void truncates_and_flattens_the_failure_summary() {
        Notification notification = scheduled();

        notification.recordFailure("line one\n\n   line   two " + "x".repeat(400), 5, LATER);

        assertThat(notification.lastError())
                .doesNotContain("\n")
                .hasSize(Notification.MAX_ERROR_LENGTH);
    }
}
