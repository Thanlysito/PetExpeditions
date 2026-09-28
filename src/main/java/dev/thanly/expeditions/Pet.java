package dev.thanly.expeditions;

import java.util.UUID;

/** Una mascota registrada por un jugador: su nivel, su aspecto y si esta de expedicion. */
public final class Pet {

    private final UUID id;
    private final String type;
    private String name;
    private String variant;
    private String collar;
    private int level = 1;
    private int xp;

    /** Id de la expedicion en curso, o null si la mascota esta libre. */
    private String expedition;
    private long endsAt;
    private boolean notified;

    public Pet(UUID id, String type) {
        this.id = id;
        this.type = type;
    }

    public UUID id() { return id; }
    public String type() { return type; }
    public String name() { return name; }
    public String variant() { return variant; }
    public String collar() { return collar; }
    public int level() { return level; }
    public int xp() { return xp; }
    public String expedition() { return expedition; }
    public long endsAt() { return endsAt; }
    public boolean notified() { return notified; }

    public void setName(String name) { this.name = name; }
    public void setVariant(String variant) { this.variant = variant; }
    public void setCollar(String collar) { this.collar = collar; }
    public void setLevel(int level) { this.level = Math.max(1, level); }
    public void setXp(int xp) { this.xp = Math.max(0, xp); }
    public void setNotified(boolean notified) { this.notified = notified; }

    /** true si esta de expedicion (aunque ya haya vuelto y falte reclamar). */
    public boolean busy() {
        return expedition != null;
    }

    /** true si ya volvio y se puede reclamar el botin. */
    public boolean ready(long now) {
        return expedition != null && now >= endsAt;
    }

    public void startExpedition(String id, long endsAt) {
        this.expedition = id;
        this.endsAt = endsAt;
        this.notified = false;
    }

    public void finishExpedition() {
        this.expedition = null;
        this.endsAt = 0;
        this.notified = false;
    }
}
