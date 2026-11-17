package com.choco.reporting.application.port;

/**
 * Writes a tabular dataset to a downloadable document.
 *
 * <p>A port, so the CSV and XLSX libraries live in the adapter module and the use case that builds a
 * dataset has no idea whether it will become a comma-separated file or a spreadsheet. That also means
 * the dataset itself — and its rule that no credential column may exist — is testable without a
 * document library on the classpath.
 */
public interface ExportWriter {

    /**
     * Serialises the dataset.
     *
     * @param dataset the columns and rows to write
     * @param format  the target format
     * @return the document bytes, ready to stream to the caller
     */
    byte[] write(com.choco.reporting.domain.export.ExportDataset dataset,
                 com.choco.reporting.domain.export.ExportFormat format);
}
