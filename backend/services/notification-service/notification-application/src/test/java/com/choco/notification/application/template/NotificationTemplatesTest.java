package com.choco.notification.application.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.notification.domain.notification.NotificationType;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Notification templates")
class NotificationTemplatesTest {

    /** Every placeholder any template can reference, so a real missing-variable bug still surfaces. */
    private static final Map<String, String> ALL_VARIABLES = Map.of(
            "customerName", "Asha", "orderNumber", "CHOCO-1", "totalAmount", "500.00",
            "amount", "500.00", "reason", "changed mind", "ingredientName", "Cocoa",
            "onHand", "1", "minimum", "2");

    static Stream<NotificationType> allTypes() {
        return Stream.of(NotificationType.values());
    }

    @ParameterizedTest
    @MethodSource("allTypes")
    @DisplayName("every notification type has a template that renders with no placeholder left behind")
    void everyTypeHasATemplate(NotificationType type) {
        assertThat(NotificationTemplates.hasTemplate(type))
                .as("%s must have a template", type)
                .isTrue();

        MessageContent content = NotificationTemplates.render(type, ALL_VARIABLES);

        assertThat(content.subject()).isNotBlank().doesNotContain("{{");
        assertThat(content.textBody()).isNotBlank().doesNotContain("{{");
    }

    @Test
    @DisplayName("an interpolated value is HTML-escaped in the wrapped HTML body")
    void escapesHtmlInTheWrapper() {
        String html = NotificationTemplates.wrapHtml("Hello <b>Asha</b>");

        assertThat(html).contains("&lt;b&gt;Asha&lt;/b&gt;").doesNotContain("<b>Asha</b>");
    }

    @Test
    @DisplayName("amounts render as rupee text, not raw paise")
    void formatsAmounts() {
        assertThat(Amounts.format(50_000L, "INR")).isEqualTo("\u20b9500.00");
    }

    @Test
    @DisplayName("a foreign currency is refused rather than printed with a rupee sign")
    void refusesForeignCurrency() {
        assertThatThrownBy(() -> Amounts.format(1_000L, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a missing template variable fails loudly rather than sending a blank message")
    void failsOnMissingVariable() {
        assertThatThrownBy(() -> NotificationTemplates.render(NotificationType.ORDER_CONFIRMED, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No value supplied");
    }

    @Test
    @DisplayName("the amount helper agrees with Money's own rendering")
    void amountsAgreeWithMoney() {
        assertThat(Amounts.format(50_000L, "INR"))
                .isEqualTo("\u20b9" + Money.inr(50_000L).toMajorUnits().toPlainString());
    }
}
