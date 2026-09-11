package com.mycompany.maildesk.messages;

public enum ContentType {
    TEXT("Texto sin formato"),
    HTML("HTML con formato");

    private final String label;

    ContentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
