package com.choco.fulfilment.domain.inventory;

/** Why an ingredient's stock moved. Kept coarse so the ledger is readable and filterable. */
public enum StockLedgerReason {

    /** Stock arrived, e.g. a purchase from a supplier. */
    RECEIVED,

    /** Stock was used making an order. */
    CONSUMED,

    /** A manual correction, e.g. after a stock-take. */
    ADJUSTED,

    /** Stock was written off, e.g. it spoiled. */
    WASTED
}
