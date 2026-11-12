package com.smartpay.integration.email;

import com.smartpay.config.EmailProperties;
import com.smartpay.exception.IntegrationException;
import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Properties;

/**
 * Authenticated SMTP sender (Jakarta Mail). The session is built directly
 * from EmailProperties so no mail auto-configuration is required. Throws
 * {@link IntegrationException} when the provider rejects the message, so
 * callers only ever report SENT after real acceptance by the SMTP server.
 */
@Component
public class EmailSender {

    private final EmailProperties properties;

    public EmailSender(EmailProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * Sends an HTML message with a PDF attachment.
     *
     * @throws IntegrationException when the SMTP provider rejects delivery
     */
    public void send(String to, String subject, String htmlBody, byte[] pdf, String pdfFileName) {
        Properties props = new Properties();
        props.put("mail.smtp.host", properties.getHost());
        props.put("mail.smtp.port", String.valueOf(properties.getPort()));
        props.put("mail.smtp.auth", "true");
        // Never hang the request thread on an unreachable mail server.
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        if (properties.isStarttls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(properties.getUsername(), properties.getPassword());
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(properties.getFrom()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");
            message.setSentDate(new Date());

            Multipart multipart = new MimeMultipart();

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            MimeBodyPart pdfPart = new MimeBodyPart();
            pdfPart.setFileName(pdfFileName);
            pdfPart.setDataHandler(new DataHandler(new ByteArrayDataSource(pdf, "application/pdf")));
            multipart.addBodyPart(pdfPart);

            message.setContent(multipart);
            Transport.send(message);
        } catch (MessagingException e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IntegrationException("SMTP delivery failed: " + detail, e);
        }
    }
}
