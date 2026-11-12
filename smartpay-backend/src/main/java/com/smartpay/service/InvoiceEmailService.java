package com.smartpay.service;

import com.smartpay.dto.response.PayPalOrderResponse;
import com.smartpay.dto.response.SendInvoiceResponse;
import com.smartpay.entity.Invoice;
import com.smartpay.entity.InvoiceStatus;
import com.smartpay.exception.IntegrationException;
import com.smartpay.integration.email.EmailSender;
import com.smartpay.integration.paypal.PayPalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.StringJoiner;

/**
 * Emails an invoice to the client: branded HTML body with invoice number,
 * amount, currency and due date, the generated PDF attached, and — only
 * when PayPal Sandbox credentials exist — a genuine checkout link created
 * through the Orders API. Delivery status is reported honestly:
 * SENT / FAILED / NOT_CONFIGURED.
 */
@Service
public class InvoiceEmailService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEmailService.class);

    private static final String SMTP_GUIDANCE =
            "Email delivery is not configured on this server. Set the SMTP_HOST, SMTP_PORT, "
                    + "SMTP_USER, SMTP_PASSWORD and SMTP_FROM environment variables and restart the "
                    + "backend. For Gmail: SMTP_HOST=smtp.gmail.com, SMTP_PORT=587, SMTP_USER and "
                    + "SMTP_FROM set to your address, SMTP_PASSWORD set to an app password "
                    + "(not your normal password).";

    private static final String PAYPAL_GUIDANCE =
            "Online payment was not included: PayPal Sandbox credentials are not configured. "
                    + "Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET (from developer.paypal.com sandbox "
                    + "accounts) and send again to include a real checkout link.";

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final PaymentService paymentService;
    private final PayPalClient payPalClient;
    private final EmailSender emailSender;

    public InvoiceEmailService(InvoiceService invoiceService,
                               InvoicePdfService invoicePdfService,
                               PaymentService paymentService,
                               PayPalClient payPalClient,
                               EmailSender emailSender) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
        this.paymentService = paymentService;
        this.payPalClient = payPalClient;
        this.emailSender = emailSender;
    }

    // ------------------------------------------------------------------- send

    @Transactional
    public SendInvoiceResponse sendInvoice(Long invoiceId) {
        Invoice invoice = invoiceService.findInvoice(invoiceId);
        String recipient = recipientOf(invoice);
        String paypalMode = payPalClient.isConfigured() ? "LIVE" : "NOT_CONFIGURED";

        if (!emailSender.isConfigured()) {
            return new SendInvoiceResponse(
                    "NOT_CONFIGURED", SMTP_GUIDANCE, recipient,
                    invoice.getInvoiceNumber(), false, paypalMode, null);
        }

        if (recipient == null) {
            return new SendInvoiceResponse(
                    "FAILED",
                    "The invoice has no client email address (and no linked customer with an email) "
                            + "— add one before sending.",
                    null, invoice.getInvoiceNumber(), false, paypalMode, null);
        }

        // Genuine PayPal checkout link only when sandbox credentials exist.
        String paymentLink = null;
        if (invoice.getStatus() != InvoiceStatus.PAID && payPalClient.isConfigured()) {
            PayPalOrderResponse order = paymentService.createPaymentLink(invoiceId);
            paymentLink = order.approveUrl();
        }
        boolean linkIncluded = paymentLink != null && !paymentLink.isBlank();

        byte[] pdf = invoicePdfService.generate(invoice);
        String html = renderHtml(invoice, paymentLink);
        String subject = "SmartPay AI invoice " + invoice.getInvoiceNumber()
                + " — " + money(invoice.getCurrency(), invoice.getTotal());

        try {
            emailSender.send(recipient, subject, html, pdf, invoice.getInvoiceNumber() + ".pdf");
        } catch (IntegrationException e) {
            log.warn("Invoice {} email to {} failed: {}", invoice.getInvoiceNumber(), recipient, e.getMessage());
            return new SendInvoiceResponse(
                    "FAILED", e.getMessage(), recipient,
                    invoice.getInvoiceNumber(), linkIncluded, paypalMode, null);
        }

        log.info("Invoice {} emailed to {}", invoice.getInvoiceNumber(), recipient);
        String message = "Invoice emailed to " + recipient + " with the PDF attached."
                + (linkIncluded ? " A PayPal checkout link is included." : "");
        return new SendInvoiceResponse(
                "SENT", message, recipient, invoice.getInvoiceNumber(),
                linkIncluded, paypalMode, LocalDateTime.now());
    }

    // -------------------------------------------------------------- html email

    private String renderHtml(Invoice invoice, String paymentLink) {
        StringJoiner rows = new StringJoiner("");
        for (var item : invoice.getItems()) {
            rows.add("<tr>")
                    .add("<td style=\"padding:8px 10px;border-bottom:1px solid #e6e8ee;\">")
                    .add(esc(item.getDescription())).add("</td>")
                    .add("<td style=\"padding:8px 10px;border-bottom:1px solid #e6e8ee;text-align:right;\">")
                    .add(plain(item.getQuantity())).add("</td>")
                    .add("<td style=\"padding:8px 10px;border-bottom:1px solid #e6e8ee;text-align:right;\">")
                    .add(plain(item.getUnitPrice())).add("</td>")
                    .add("<td style=\"padding:8px 10px;border-bottom:1px solid #e6e8ee;text-align:right;\">")
                    .add(plain(item.getAmount())).add("</td>")
                    .add("</tr>");
        }

        String paySection;
        if (paymentLink != null) {
            paySection = "<p style=\"margin:26px 0 8px;\">"
                    + "<a href=\"" + esc(paymentLink) + "\" "
                    + "style=\"display:inline-block;background:#0070ba;color:#ffffff;"
                    + "text-decoration:none;padding:12px 26px;border-radius:6px;"
                    + "font-weight:bold;font-size:15px;\">Pay with PayPal</a></p>"
                    + "<p style=\"color:#667085;font-size:12px;margin:4px 0 0;\">If the button does not "
                    + "work, open this link: " + esc(paymentLink) + "</p>";
        } else {
            paySection = "<p style=\"margin:26px 0 0;color:#667085;font-size:13px;\">"
                    + "Online payment via PayPal is not enabled for this sender. "
                    + "Please arrange payment as agreed with the sender.</p>";
        }

        return """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#222633;max-width:640px;">
                  <div style="border-bottom:3px solid #6c5ce7;padding-bottom:14px;margin-bottom:18px;">
                    <span style="font-size:22px;font-weight:bold;color:#6c5ce7;">SmartPay AI</span>
                    <span style="float:right;font-size:20px;font-weight:bold;">INVOICE %s</span>
                  </div>
                  <p style="font-size:14px;">Hello %s,</p>
                  <p style="font-size:14px;line-height:1.6;">
                    please find attached invoice <strong>%s</strong> for
                    <strong>%s</strong>, due on <strong>%s</strong>.
                    A PDF copy is attached to this email.
                  </p>
                  <table style="width:100%%;border-collapse:collapse;font-size:13px;margin-top:18px;">
                    <thead>
                      <tr style="background:#222633;color:#ffffff;">
                        <th style="padding:8px 10px;text-align:left;">Description</th>
                        <th style="padding:8px 10px;text-align:right;">Qty</th>
                        <th style="padding:8px 10px;text-align:right;">Unit price</th>
                        <th style="padding:8px 10px;text-align:right;">Amount</th>
                      </tr>
                    </thead>
                    <tbody>%s</tbody>
                  </table>
                  <table style="width:100%%;font-size:13px;margin-top:12px;">
                    <tr>
                      <td style="text-align:right;padding:4px 10px;color:#667085;\">Subtotal</td>
                      <td style="text-align:right;padding:4px 10px;width:110px;">%s</td>
                    </tr>
                    <tr>
                      <td style="text-align:right;padding:4px 10px;color:#667085;\">Tax / GST (%s%%)</td>
                      <td style="text-align:right;padding:4px 10px;">%s</td>
                    </tr>
                    <tr>
                      <td style="text-align:right;padding:8px 10px;font-weight:bold;font-size:15px;\">Total</td>
                      <td style="text-align:right;padding:8px 10px;font-weight:bold;font-size:15px;">%s</td>
                    </tr>
                  </table>
                  %s
                  %s
                  <p style="margin-top:28px;font-size:13px;">Thank you for your business.</p>
                  <p style="color:#667085;font-size:12px;">Generated by SmartPay AI · invoice %s</p>
                </div>
                """.formatted(
                esc(invoice.getInvoiceNumber()),
                esc(invoice.getClientName()),
                esc(invoice.getInvoiceNumber()),
                money(invoice.getCurrency(), invoice.getTotal()),
                esc(dateOf(invoice)),
                rows,
                money(null, invoice.getSubtotal()),
                plain(invoice.getTaxRate()),
                money(null, invoice.getTaxAmount()),
                money(invoice.getCurrency(), invoice.getTotal()),
                paySection,
                notesSection(invoice),
                esc(invoice.getInvoiceNumber()));
    }

    private String notesSection(Invoice invoice) {
        if (invoice.getNotes() == null || invoice.getNotes().isBlank()) {
            return "";
        }
        return "<p style=\"margin-top:18px;color:#667085;font-size:13px;\">Notes: "
                + esc(invoice.getNotes()) + "</p>";
    }

    // ----------------------------------------------------------------- helpers

    private String recipientOf(Invoice invoice) {
        if (invoice.getClientEmail() != null && !invoice.getClientEmail().isBlank()) {
            return invoice.getClientEmail().trim();
        }
        if (invoice.getCustomer() != null && invoice.getCustomer().getEmail() != null
                && !invoice.getCustomer().getEmail().isBlank()) {
            return invoice.getCustomer().getEmail().trim();
        }
        return null;
    }

    private String dateOf(Invoice invoice) {
        return invoice.getDueDate() == null ? "on receipt" : invoice.getDueDate().toString();
    }

    private String money(String currency, BigDecimal value) {
        String amount = String.format(java.util.Locale.US, "%,.2f",
                value == null ? BigDecimal.ZERO : value);
        return currency == null ? amount : currency + " " + amount;
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
