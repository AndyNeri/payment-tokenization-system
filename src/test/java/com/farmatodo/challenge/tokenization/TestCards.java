package com.farmatodo.challenge.tokenization;

/** Public test card numbers (Luhn-valid, never real accounts). */
public final class TestCards {

    public static final String VISA = "4111111111111111";
    public static final String VISA_13 = "4222222222222";
    public static final String MASTERCARD = "5555555555554444";
    public static final String MASTERCARD_2_SERIES = "2223003122003222";
    public static final String AMEX = "378282246310005";
    public static final String DISCOVER = "6011111111111117";
    public static final String DINERS = "30569309025904";
    public static final String VISA_BAD_LUHN = "4111111111111112";

    private TestCards() {
    }
}
