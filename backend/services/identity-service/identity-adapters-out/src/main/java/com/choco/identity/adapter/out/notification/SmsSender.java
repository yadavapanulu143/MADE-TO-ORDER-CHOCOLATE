package com.choco.identity.adapter.out.notification;

/**
 * Sends an SMS.
 *
 * <p>A port rather than a direct provider call because no SMS provider has been selected. The
 * business requirement is to send verification codes by SMS; which gateway delivers them is a
 * commercial decision that has not been made, and hard-coding a vendor now would be a guess.
 */
interface SmsSender {

    void send(String mobileNumber, String message);
}
