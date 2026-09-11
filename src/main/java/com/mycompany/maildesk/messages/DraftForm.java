package com.mycompany.maildesk.messages;

/** Datos del formulario de redacción (también usado por el autoguardado JSON). */
public class DraftForm {
    private String to = "";
    private String cc = "";
    private String bcc = "";
    private String subject = "";
    private String body = "";
    private ContentType contentType = ContentType.HTML;
    private boolean includeSignature = true;
    private String submissionToken;

    public static DraftForm from(Message m) {
        DraftForm f = new DraftForm();
        f.to = nz(m.getToRecipients());
        f.cc = nz(m.getCcRecipients());
        f.bcc = nz(m.getBccRecipients());
        f.subject = nz(m.getSubject());
        f.body = nz(m.getBodyHtml());
        f.contentType = m.getContentType();
        f.includeSignature = m.isIncludeSignature();
        return f;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    public String getCc() { return cc; }
    public void setCc(String cc) { this.cc = cc; }
    public String getBcc() { return bcc; }
    public void setBcc(String bcc) { this.bcc = bcc; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }
    public boolean isIncludeSignature() { return includeSignature; }
    public void setIncludeSignature(boolean includeSignature) { this.includeSignature = includeSignature; }
    public String getSubmissionToken() { return submissionToken; }
    public void setSubmissionToken(String submissionToken) { this.submissionToken = submissionToken; }
}
