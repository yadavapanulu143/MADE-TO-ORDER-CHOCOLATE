package com.choco.identity.adapter.out.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Captures SMS messages during integration tests.
 *
 * <p>The production SMS sender is a logging sink that deliberately withholds the message body, so a
 * test cannot read the mobile code from it. This replaces only the leaf {@link SmsSender} — the real
 * {@code NotificationAdapter}, the real {@code OtpDeliveryPort} path and the real message formatting
 * are all still exercised. It captures the code so a test can complete mobile verification without
 * weakening the production behaviour or reaching into the database for a hash it could not reverse.
 *
 * <p>Declared {@code @Primary} so it wins over the shipping {@code loggingSmsSender} bean, which is
 * still created but never selected. The class lives in the same package as {@link SmsSender} because
 * that interface is package-private.
 */
@TestConfiguration
public class CapturingSmsConfiguration {

    @Bean
    @Primary
    SmsSender capturingSmsSender() {
        return new CapturingSmsSender();
    }

    /** The newest SMS code delivered during this test, for the base class to read. */
    public static String latestSmsCode() {
        return CapturingSmsSender.latestCode();
    }

    /** Clears captured SMS between tests. */
    public static void clearSms() {
        CapturingSmsSender.clear();
    }

    /** Test-visible SMS sink; the interface is package-private, so this type is too. */
    static final class CapturingSmsSender implements SmsSender {

        private static final Pattern CODE = Pattern.compile("\\b([0-9]{6})\\b");
        private static final List<String> MESSAGES = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void send(String mobileNumber, String message) {
            MESSAGES.add(message);
        }

        /** The newest delivered code, or {@code null} if nothing has been sent yet. */
        static String latestCode() {
            synchronized (MESSAGES) {
                for (int i = MESSAGES.size() - 1; i >= 0; i--) {
                    Matcher matcher = CODE.matcher(MESSAGES.get(i));
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
            return null;
        }

        static void clear() {
            MESSAGES.clear();
        }
    }
}
