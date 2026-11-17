package com.choco.fulfilment.domain.inventory;

/**
 * Unit an ingredient is measured in.
 *
 * <p>A closed set rather than free text, because a quantity is meaningless without its unit and a
 * typo would silently change the meaning of a recipe: "500 g" and "500 kg" are the same number.
 */
public enum UnitOfMeasure {

    GRAM("g"),
    KILOGRAM("kg"),
    MILLILITRE("ml"),
    LITRE("l"),
    PIECE("pc"),
    PACKET("pkt");

    private final String symbol;

    UnitOfMeasure(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
