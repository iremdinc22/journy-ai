package com.journy.backend.common.exception;

/** Controlled result when a requested itinerary or edit cannot supply verified places. */
public class InsufficientDestinationDataException extends RuntimeException {
    public static final String CODE = "INSUFFICIENT_DESTINATION_DATA";
    private final int required;
    private final int available;

    public InsufficientDestinationDataException(int required, int available) {
        super("We couldn't find enough reliable places for this destination yet. Please try again or choose a shorter trip.");
        this.required = required;
        this.available = available;
    }

    public int required() { return required; }
    public int available() { return available; }
}
