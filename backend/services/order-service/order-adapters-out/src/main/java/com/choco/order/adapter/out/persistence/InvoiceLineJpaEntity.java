package com.choco.order.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** One priced line of an invoice, as stored. */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLineJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private InvoiceJpaEntity invoice;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "variant_label", nullable = false, length = 80)
    private String variantLabel;

    @Column(name = "sku", length = 64)
    private String sku;

    @Column(name = "unit_price_minor_units", nullable = false)
    private long unitPriceMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected InvoiceLineJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public InvoiceJpaEntity getInvoice() {
        return invoice;
    }

    public void setInvoice(InvoiceJpaEntity invoice) {
        this.invoice = invoice;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getVariantLabel() {
        return variantLabel;
    }

    public void setVariantLabel(String variantLabel) {
        this.variantLabel = variantLabel;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public long getUnitPriceMinorUnits() {
        return unitPriceMinorUnits;
    }

    public void setUnitPriceMinorUnits(long unitPriceMinorUnits) {
        this.unitPriceMinorUnits = unitPriceMinorUnits;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
