package com.makambox.studio.model;

public enum EventType {
    SHORT_CONTACT("Kısa temas"), PASSING("Geçiş"), SUSTAINED("Uzatma"), CADENTIAL_HOLD("Kalış");

    private final String label;
    EventType(String label) { this.label = label; }
    public String label() { return label; }
    public boolean measurable() { return this != SHORT_CONTACT; }
    public boolean holdLike() { return this == SUSTAINED || this == CADENTIAL_HOLD; }
    @Override public String toString() { return label; }
}
