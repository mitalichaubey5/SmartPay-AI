package com.smartpay.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartpay.entity.Invoice;
import com.smartpay.entity.InvoiceItem;
import com.smartpay.entity.InvoiceStatus;
import com.smartpay.exception.IntegrationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a professional, branded invoice PDF with OpenPDF: SmartPay AI
 * header, client details, line items, tax and total. Used both by the
 * download endpoint and as the attachment when emailing an invoice.
 */
@Service
public class InvoicePdfService {

    private static final Color BRAND = new Color(0x6C, 0x5C, 0xE7);
    private static final Color DARK = new Color(0x22, 0x26, 0x33);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x84);
    private static final Color LINE = new Color(0xD9, 0xDE, 0xE8);
    private static final Color GREEN = new Color(0x1B, 0x9E, 0x4B);

    private static final Font BRAND_FONT = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND);
    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 26, Font.BOLD, DARK);
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 8.5f, Font.BOLD, MUTED);
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, DARK);
    private static final Font BOLD_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, DARK);
    private static final Font TABLE_HEAD_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TOTAL_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, BRAND);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final InvoiceService invoiceService;

    public InvoicePdfService(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** Generated PDF plus the file name to serve it under. */
    public record PdfResult(String fileName, byte[] content) {
    }

    /**
     * Loads the invoice (user-scoped) and renders it to PDF inside one
     * transaction, so lazy collections (items, customer) are available.
     */
    @Transactional(readOnly = true)
    public PdfResult generateFor(Long invoiceId) {
        Invoice invoice = invoiceService.findInvoice(invoiceId);
        return new PdfResult(invoice.getInvoiceNumber() + ".pdf", generate(invoice));
    }

    /**
     * Renders an already-loaded invoice (the caller must be inside a
     * transaction if the entity's collections are lazy).
     */
    public byte[] generate(Invoice invoice) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 48, 48, 52, 44);
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, invoice);
            addClientBlock(document, invoice);
            addItemsTable(document, invoice);
            addTotals(document, invoice);
            addNotes(document, invoice);
            addFooter(document, invoice);

            document.close();
            return out.toByteArray();
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("Could not generate the invoice PDF: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ header

    private void addHeader(Document document, Invoice invoice) {
        PdfPTable header = new PdfPTable(new float[]{62, 38});
        header.setWidthPercentage(100);
        header.getDefaultCell().setBorder(0);

        PdfPCell brandCell = new PdfPCell(new Phrase("SmartPay AI", BRAND_FONT));
        brandCell.setBorder(0);
        brandCell.setPaddingBottom(10f);
        header.addCell(brandCell);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(0);
        titleCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("INVOICE", TITLE_FONT);
        title.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(title);
        Paragraph number = new Paragraph(invoice.getInvoiceNumber(), BOLD_FONT);
        number.setAlignment(Element.ALIGN_RIGHT);
        titleCell.addElement(number);
        header.addCell(titleCell);
        document.add(header);

        PdfPTable meta = new PdfPTable(new float[]{25, 25, 25, 25});
        meta.setWidthPercentage(100);
        meta.getDefaultCell().setBorder(0);
        meta.addCell(metaCell("ISSUE DATE", formatDate(invoice.getCreatedAt()), BODY_FONT));
        meta.addCell(metaCell("DUE DATE", formatDate(invoice.getDueDate()), BODY_FONT));
        meta.addCell(metaCell("STATUS", invoice.getStatus().name(), statusFont(invoice.getStatus())));
        PdfPCell totalCell = metaCell("TOTAL", money(invoice.getCurrency(), invoice.getTotal()), BOLD_FONT);
        totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.addCell(totalCell);
        document.add(meta);

        addSpacer(document, 14f);
    }

    private PdfPCell metaCell(String label, String value, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setPaddingBottom(2f);
        cell.addElement(new Paragraph(label, LABEL_FONT));
        cell.addElement(new Paragraph(value, valueFont));
        return cell;
    }

    // ------------------------------------------------------------ client block

    private void addClientBlock(Document document, Invoice invoice) {
        PdfPTable block = new PdfPTable(1);
        block.setWidthPercentage(100);
        block.getDefaultCell().setBorder(0);

        PdfPCell label = new PdfPCell(new Phrase("BILLED TO", LABEL_FONT));
        label.setBorder(0);
        label.setPaddingBottom(4f);
        block.addCell(label);

        PdfPCell name = new PdfPCell(new Phrase(nullSafe(invoice.getClientName()), BOLD_FONT));
        name.setBorder(0);
        block.addCell(name);

        if (invoice.getClientEmail() != null && !invoice.getClientEmail().isBlank()) {
            PdfPCell email = new PdfPCell(new Phrase(invoice.getClientEmail(), BODY_FONT));
            email.setBorder(0);
            block.addCell(email);
        }
        if (invoice.getCustomer() != null && invoice.getCustomer().getCompany() != null
                && !invoice.getCustomer().getCompany().isBlank()) {
            PdfPCell company = new PdfPCell(new Phrase(invoice.getCustomer().getCompany(), BODY_FONT));
            company.setBorder(0);
            block.addCell(company);
        }
        document.add(block);

        addSpacer(document, 12f);
        Paragraph rule = new Paragraph(" ");
        // A thin divider line between the client block and the items table.
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell dividerCell = new PdfPCell(new Phrase(" "));
        dividerCell.setFixedHeight(1.2f);
        dividerCell.setBorder(0);
        dividerCell.setBackgroundColor(LINE);
        divider.addCell(dividerCell);
        document.add(divider);
        addSpacer(document, 8f);
    }

    // ------------------------------------------------------------- items table

    private void addItemsTable(Document document, Invoice invoice) {
        PdfPTable table = new PdfPTable(new float[]{58, 12, 15, 15});
        table.setWidthPercentage(100);

        String[] heads = {"DESCRIPTION", "QTY", "UNIT PRICE", "AMOUNT"};
        int[] alignments = {Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT};
        for (int i = 0; i < heads.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(heads[i], TABLE_HEAD_FONT));
            cell.setBackgroundColor(DARK);
            cell.setHorizontalAlignment(alignments[i]);
            cell.setPadding(7f);
            table.addCell(cell);
        }

        for (InvoiceItem item : invoice.getItems()) {
            table.addCell(textCell(item.getDescription(), Element.ALIGN_LEFT));
            table.addCell(textCell(stripTrailingZeros(item.getQuantity()), Element.ALIGN_RIGHT));
            table.addCell(textCell(money(null, item.getUnitPrice()), Element.ALIGN_RIGHT));
            table.addCell(textCell(money(null, item.getAmount()), Element.ALIGN_RIGHT));
        }

        // Guarantee at least a few rows so short invoices still look right.
        int fillerRows = Math.max(0, 3 - invoice.getItems().size());
        for (int i = 0; i < fillerRows; i++) {
            table.addCell(textCell("", Element.ALIGN_LEFT));
            table.addCell(textCell("", Element.ALIGN_RIGHT));
            table.addCell(textCell("", Element.ALIGN_RIGHT));
            table.addCell(textCell("", Element.ALIGN_RIGHT));
        }

        document.add(table);
        addSpacer(document, 12f);
    }

    // ------------------------------------------------------------------ totals

    private void addTotals(Document document, Invoice invoice) {
        PdfPTable totals = new PdfPTable(new float[]{70, 30});
        totals.setWidthPercentage(100);
        totals.getDefaultCell().setBorder(0);

        totals.addCell(noBorderCell(""));
        totals.addCell(rowCell("Subtotal", money(null, invoice.getSubtotal()), false));
        totals.addCell(noBorderCell(""));
        totals.addCell(rowCell(
                "Tax / GST (" + stripTrailingZeros(invoice.getTaxRate()) + "%)",
                money(null, invoice.getTaxAmount()), false));
        totals.addCell(noBorderCell(""));

        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", BOLD_FONT));
        totalLabel.setBorder(0);
        totalLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
        totalLabel.setPaddingTop(6f);
        totals.addCell(totalLabel);

        PdfPCell totalValue = new PdfPCell(new Phrase(
                money(invoice.getCurrency(), invoice.getTotal()), TOTAL_FONT));
        totalValue.setBorder(0);
        totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalValue.setPaddingTop(6f);
        totals.addCell(totalValue);

        document.add(totals);
    }

    // ------------------------------------------------------------------- notes

    private void addNotes(Document document, Invoice invoice) {
        if (invoice.getNotes() == null || invoice.getNotes().isBlank()) {
            return;
        }
        addSpacer(document, 14f);
        Paragraph label = new Paragraph("NOTES", LABEL_FONT);
        document.add(label);
        Paragraph notes = new Paragraph(invoice.getNotes(), BODY_FONT);
        document.add(notes);
    }

    // ------------------------------------------------------------------ footer

    private void addFooter(Document document, Invoice invoice) {
        addSpacer(document, 28f);

        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell dividerCell = new PdfPCell(new Phrase(" "));
        dividerCell.setFixedHeight(1.2f);
        dividerCell.setBorder(0);
        dividerCell.setBackgroundColor(LINE);
        divider.addCell(dividerCell);
        document.add(divider);
        addSpacer(document, 6f);

        Paragraph thanks = new Paragraph("Thank you for your business.", BOLD_FONT);
        document.add(thanks);
        Paragraph footer = new Paragraph(
                "Generated by SmartPay AI on " + formatDate(LocalDateTime.now())
                        + " · Invoice " + invoice.getInvoiceNumber(),
                FOOTER_FONT);
        document.add(footer);
    }

    // ------------------------------------------------------------------ helpers

    private PdfPCell textCell(String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(nullSafe(text), BODY_FONT));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(7f);
        cell.setBorderColor(LINE);
        return cell;
    }

    /** A right-aligned label/value pair with no borders. */
    private PdfPCell rowCell(String label, String value, boolean bold) {
        PdfPCell pair = new PdfPCell();
        pair.setBorder(0);
        PdfPTable inner = new PdfPTable(new float[]{55, 45});
        inner.setWidthPercentage(100);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, bold ? BOLD_FONT : BODY_FONT));
        labelCell.setBorder(0);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        inner.addCell(labelCell);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, bold ? BOLD_FONT : BODY_FONT));
        valueCell.setBorder(0);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        inner.addCell(valueCell);
        pair.addElement(inner);
        return pair;
    }

    private PdfPCell noBorderCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY_FONT));
        cell.setBorder(0);
        return cell;
    }

    private Font statusFont(InvoiceStatus status) {
        if (status == InvoiceStatus.PAID) {
            return new Font(Font.HELVETICA, 10, Font.BOLD, GREEN);
        }
        return BOLD_FONT;
    }

    private void addSpacer(Document document, float height) {
        Paragraph spacer = new Paragraph(" ");
        spacer.setLeading(height);
        document.add(spacer);
    }

    private String money(String currency, BigDecimal value) {
        String amount = String.format(Locale.US, "%,.2f", value == null ? BigDecimal.ZERO : value);
        return currency == null ? amount : currency + " " + amount;
    }

    private String stripTrailingZeros(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.toPlainString();
    }

    private String formatDate(LocalDate date) {
        return date == null ? "—" : DATE_FORMAT.format(date);
    }

    private String formatDate(LocalDateTime date) {
        return date == null ? "—" : DATE_FORMAT.format(date);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
