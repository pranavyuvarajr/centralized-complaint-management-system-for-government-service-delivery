package com.project.complaint.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds the urgent-alert emails (plain text + simple HTML). Pure and side-effect free.
 * Deliberately has no input for the citizen's phone number, and the link carries no token:
 * the recipient must log in, and the server decides what they may see.
 */
public final class UrgentAlertEmailBuilder {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private UrgentAlertEmailBuilder() {}

    public record Data(Long complaintId, String reference, String title, String category, String address,
                       LocalDateTime submittedAt, String description, String urgencyReason, String assignedTo,
                       int reminderNumber, int reminderTotal, long hoursPending) {}

    public static String link(String frontendBaseUrl, boolean admin, Long complaintId) {
        String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        return base + (admin ? "/admin/complaint/" : "/official/complaint/") + complaintId;
    }

    public static EmailMessage build(String toAddress, String recipientName, boolean admin,
                                     UrgentAlertRequested.Kind kind, Data d, String frontendBaseUrl) {
        String link = link(frontendBaseUrl, admin, d.complaintId());
        String assigned = d.assignedTo() == null ? "Not assigned to an official yet" : d.assignedTo();

        String subject;
        String intro;
        String action;
        switch (kind) {
            case REMINDER -> {
                subject = "[URGENT] Reminder " + d.reminderNumber() + " of " + d.reminderTotal() + ": " + d.reference() + " - " + d.title();
                intro = "Reminder " + d.reminderNumber() + " of " + d.reminderTotal() + ": this urgent complaint has been waiting "
                        + d.hoursPending() + " hours for a decision on its urgency.";
                action = d.assignedTo() == null && admin
                        ? "It is not assigned to an official yet. Please assign it to an official, who can then verify it."
                        : "Please open the complaint, call the citizen to verify the situation, then either confirm it as urgent or mark it as normal (with a reason).";
            }
            case ESCALATION -> {
                subject = "[URGENT] Escalation: no decision on " + d.reference() + " - " + d.title();
                intro = "Escalation: " + d.assignedTo() + " was reminded " + d.reminderTotal() + " times over "
                        + d.hoursPending() + " hours and has still not decided whether this complaint is urgent.";
                action = "Please open the complaint and decide it yourself (confirm as urgent or mark as normal), or reassign it to another official.";
            }
            default -> {
                subject = "[URGENT] New urgent complaint " + d.reference() + " - " + d.title();
                intro = d.assignedTo() == null
                        ? "A citizen has submitted an urgent complaint that is not assigned to any official."
                        : "A citizen has submitted an urgent complaint that is assigned to " + d.assignedTo() + ".";
                action = d.assignedTo() == null && admin
                        ? "Please assign it to an official as soon as possible. The assigned official can then call the citizen to verify it."
                        : "Please open the complaint, call the citizen to verify the situation, then either confirm it as urgent or mark it as normal (with a reason).";
            }
        }

        StringBuilder text = new StringBuilder();
        text.append("Hello ").append(recipientName).append(",\n\n").append(intro).append("\n\n")
                .append("Reference:   ").append(d.reference()).append('\n')
                .append("Title:       ").append(d.title()).append('\n')
                .append("Category:    ").append(d.category()).append('\n');
        if (d.address() != null && !d.address().isBlank()) text.append("Location:    ").append(d.address()).append('\n');
        text.append("Submitted:   ").append(d.submittedAt() == null ? "-" : d.submittedAt().format(WHEN)).append('\n')
                .append("Assigned to: ").append(assigned).append("\n\n")
                .append("Description from the citizen:\n").append(nullToDash(d.description())).append("\n\n")
                .append("Why the citizen says it is urgent:\n").append(nullToDash(d.urgencyReason())).append("\n\n")
                .append("What to do next: ").append(action).append("\n\n")
                .append("Open the complaint (you will be asked to log in): ").append(link).append('\n');

        StringBuilder html = new StringBuilder();
        html.append("<p>Hello ").append(esc(recipientName)).append(",</p><p>").append(esc(intro)).append("</p>")
                .append("<table cellpadding=\"4\" style=\"border-collapse:collapse\">")
                .append(row("Reference", d.reference())).append(row("Title", d.title())).append(row("Category", d.category()));
        if (d.address() != null && !d.address().isBlank()) html.append(row("Location", d.address()));
        html.append(row("Submitted", d.submittedAt() == null ? "-" : d.submittedAt().format(WHEN)))
                .append(row("Assigned to", assigned)).append("</table>")
                .append("<p><strong>Description from the citizen</strong><br>").append(escMultiline(nullToDash(d.description()))).append("</p>")
                .append("<p><strong>Why the citizen says it is urgent</strong><br>").append(escMultiline(nullToDash(d.urgencyReason()))).append("</p>")
                .append("<p><strong>What to do next:</strong> ").append(esc(action)).append("</p>")
                .append("<p><a href=\"").append(link).append("\">Open the complaint</a> (you will be asked to log in)</p>");

        return new EmailMessage(toAddress, subject, text.toString(), html.toString());
    }

    private static String row(String label, String value) {
        return "<tr><td><strong>" + esc(label) + "</strong></td><td>" + esc(value) + "</td></tr>";
    }

    private static String nullToDash(String s) { return (s == null || s.isBlank()) ? "-" : s; }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escMultiline(String s) { return esc(s).replace("\n", "<br>"); }
}
