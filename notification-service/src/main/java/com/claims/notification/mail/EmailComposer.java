package com.claims.notification.mail;

import com.claims.notification.config.NotificationProperties;
import com.claims.notification.event.ClaimStatusChanged;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Turns a status-change event into the email the claimant reads, as plain text and as HTML (every email carries both).
 * The wording is deliberately short and says what happens next. Two things are left out on purpose: the officer's notes (they
 * are not in the event, and are shown only to a signed-in claimant) and any currency (the claims have none; amounts are shown with their market).
 */
@Component
public class EmailComposer {
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final String portalUrl;

    public EmailComposer(NotificationProperties properties) {
        String url = properties.portalUrl() == null ? "" : properties.portalUrl().trim();
        this.portalUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** What an email says, apart from the facts that are the same for every status. */
    private record Wording(String subject, String headline, String body, String actionLabel) {}

    public EmailMessage compose(ClaimStatusChanged event) {
        String number = plain(event.claimNumber());
        Wording wording = wording(event, number);
        String greeting = "Hello " + (blank(event.claimantName()) ? "there" : plain(event.claimantName())) + ",";
        String link = portalUrl + "/claims/" + event.claimId();
        String actionLabel = wording.actionLabel() != null ? wording.actionLabel() : "View your claim";
        List<String[]> facts = facts(event, number);

        return new EmailMessage(event.eventId(), event.claimantEmail().trim(), plain(event.claimantName()),
                wording.subject(), text(greeting, wording, facts, actionLabel, link), html(greeting, wording, facts, actionLabel, link));
    }

    private Wording wording(ClaimStatusChanged e, String number) {
        String to = e.toStatus() == null ? "" : e.toStatus();
        String from = e.fromStatus() == null ? "" : e.fromStatus();
        return switch (to) {
            case "SUBMITTED" -> new Wording("We have received your claim " + number, "Claim received",
                    "Thank you for reporting your incident. Your claim is waiting for a claims officer to pick it up. "
                            + "We will email you each time its status changes.", null);
            case "UNDER_REVIEW" -> "MORE_INFO_REQUIRED".equals(from)
                    ? new Wording("Thank you: claim " + number + " is back under review", "Back under review",
                            "We have what we need and a claims officer is reviewing your claim again.", null)
                    : new Wording("Your claim " + number + " is now under review", "Under review",
                            "A claims officer has started to review your claim.", null);
            case "MORE_INFO_REQUIRED" -> new Wording("Action needed: more information for claim " + number, "More information needed",
                    "The claims officer needs more information before they can continue. Please sign in to read the question and send your answer.",
                    "Answer the question");
            case "APPROVED" -> new Wording("Your claim " + number + " has been approved", "Claim approved",
                    "Your claim has been approved. We will email you again when it is marked as settled. "
                            + "Sign in to read the officer's decision note.", null);
            case "REJECTED" -> new Wording("Decision on your claim " + number, "Claim not approved",
                    "Your claim has not been approved. Sign in to read the reason given by the claims officer.", null);
            case "SETTLED" -> new Wording("Your claim " + number + " has been settled", "Claim settled",
                    "Your claim has been settled. Sign in to see the details.", null);
            default -> new Wording("Update on your claim " + number, "Claim updated",
                    "The status of your claim is now: " + humanise(to) + ".", null);
        };
    }

    private List<String[]> facts(ClaimStatusChanged e, String number) {
        List<String[]> facts = new ArrayList<>();
        facts.add(new String[] {"Claim number", number});
        if (!blank(e.claimType())) {
            facts.add(new String[] {"Type", humanise(e.claimType())});
        }
        if (e.amountClaimed() != null) {
            facts.add(new String[] {"Amount claimed", amount(e.amountClaimed(), e.market())});
        }
        facts.add(new String[] {"Status", humanise(e.toStatus())});
        if (e.occurredAt() != null) {
            facts.add(new String[] {"Updated", WHEN.format(e.occurredAt())});
        }
        return facts;
    }

    /** "2,400.75 (SG)": the claims have no currency, so the amount is shown with its market, as in the app. */
    static String amount(BigDecimal amount, String market) {
        String formatted = String.format(Locale.ENGLISH, "%,.2f", amount);
        return blank(market) ? formatted : formatted + " (" + plain(market).toUpperCase(Locale.ROOT) + ")";
    }

    /** {@code MORE_INFO_REQUIRED} becomes {@code More information required}. */
    static String humanise(String code) {
        if (blank(code)) {
            return "";
        }
        String words = plain(code).replace('_', ' ').toLowerCase(Locale.ENGLISH);
        if (words.equals("more info required")) {
            words = "more information required";
        }
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String text(String greeting, Wording w, List<String[]> facts, String actionLabel, String link) {
        StringBuilder sb = new StringBuilder();
        sb.append(greeting).append("\n\n").append(w.body()).append("\n\n");
        for (String[] fact : facts) {
            sb.append(fact[0]).append(": ").append(fact[1]).append('\n');
        }
        sb.append('\n').append(actionLabel).append(": ").append(link).append("\n\n");
        sb.append("This is an automated message from the Claims Platform. Please do not reply to it.\n");
        return sb.toString();
    }

    private static String html(String greeting, Wording w, List<String[]> facts, String actionLabel, String link) {
        StringBuilder rows = new StringBuilder();
        for (String[] fact : facts) {
            rows.append("<tr><td style=\"padding:6px 16px 6px 0;color:#5b6678\">").append(esc(fact[0]))
                    .append("</td><td style=\"padding:6px 0;font-weight:600\">").append(esc(fact[1])).append("</td></tr>");
        }
        return "<!doctype html><html><body style=\"margin:0;padding:24px;background:#f4f6fb;font-family:Segoe UI,Arial,sans-serif;color:#17202e\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:560px;margin:0 auto;background:#ffffff;border-radius:12px;border:1px solid #e1e6ef\">"
                + "<tr><td style=\"padding:20px 28px;background:#0e2147;color:#ffffff;border-radius:12px 12px 0 0;font-size:16px;font-weight:600\">Claims Platform</td></tr>"
                + "<tr><td style=\"padding:28px\">"
                + "<h1 style=\"margin:0 0 16px;font-size:22px\">" + esc(w.headline()) + "</h1>"
                + "<p style=\"margin:0 0 12px\">" + esc(greeting) + "</p>"
                + "<p style=\"margin:0 0 20px;line-height:1.5\">" + esc(w.body()) + "</p>"
                + "<table role=\"presentation\" style=\"margin:0 0 24px;font-size:14px\">" + rows + "</table>"
                + "<p style=\"margin:0 0 24px\"><a href=\"" + esc(link) + "\" style=\"display:inline-block;padding:11px 20px;background:#2457e6;"
                + "color:#ffffff;text-decoration:none;border-radius:8px;font-weight:600\">" + esc(actionLabel) + "</a></p>"
                + "<p style=\"margin:0;font-size:12px;color:#5b6678\">This is an automated message from the Claims Platform. Please do not reply to it.</p>"
                + "</td></tr></table></body></html>";
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    /** Control characters (including line breaks) become spaces, so a value can never break a subject line or the text body. */
    private static String plain(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
