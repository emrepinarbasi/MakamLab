package com.makambox.studio.model;

public final class PitchOccurrence {
    private final int index;
    private final double startSeconds;
    private final double endSeconds;
    private final double observedCent;
    private final double expectedCent;
    private final double stabilityCent;
    private final double entryDeviationCent;
    private final double settledDeviationCent;
    private final double exitDeviationCent;
    private final double vibratoSpanCent;
    private final boolean phraseFinal;
    private final double confidence;
    private final MelodicDirection entryDirection;
    private final MelodicDirection exitDirection;
    private MelodicDirection direction;
    private EventType eventType;

    public PitchOccurrence(int index, double startSeconds, double endSeconds,
                           double observedCent, double expectedCent, double stabilityCent,
                           double entryDeviationCent, double settledDeviationCent,
                           double exitDeviationCent, double vibratoSpanCent,
                           boolean phraseFinal, double confidence,
                           MelodicDirection entryDirection, MelodicDirection exitDirection,
                           MelodicDirection direction, EventType eventType) {
        this.index = index;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.observedCent = observedCent;
        this.expectedCent = expectedCent;
        this.stabilityCent = stabilityCent;
        this.entryDeviationCent = entryDeviationCent;
        this.settledDeviationCent = settledDeviationCent;
        this.exitDeviationCent = exitDeviationCent;
        this.vibratoSpanCent = vibratoSpanCent;
        this.phraseFinal = phraseFinal;
        this.confidence = confidence;
        this.entryDirection = entryDirection;
        this.exitDirection = exitDirection;
        this.direction = direction;
        this.eventType = eventType;
    }

    public int index() { return index; }
    public double startSeconds() { return startSeconds; }
    public double endSeconds() { return endSeconds; }
    public double durationSeconds() { return endSeconds - startSeconds; }
    public double observedCent() { return observedCent; }
    public double expectedCent() { return expectedCent; }
    public double deviationCent() { return observedCent - expectedCent; }
    public double deviationComma() { return deviationCent() / (1200.0 / 53.0); }
    public double stabilityCent() { return stabilityCent; }
    public double entryDeviationCent() { return entryDeviationCent; }
    public double settledDeviationCent() { return settledDeviationCent; }
    public double exitDeviationCent() { return exitDeviationCent; }
    public double vibratoSpanCent() { return vibratoSpanCent; }
    public boolean phraseFinal() { return phraseFinal; }
    public double confidence() { return confidence; }
    public MelodicDirection entryDirection() { return entryDirection; }
    public MelodicDirection exitDirection() { return exitDirection; }
    public MelodicDirection direction() { return direction; }
    public EventType eventType() { return eventType; }
    public void setDirection(MelodicDirection direction) { this.direction = direction; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
}
