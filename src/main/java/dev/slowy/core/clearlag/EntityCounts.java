package dev.slowy.core.clearlag;

public record EntityCounts(
        int items,
        int monsters,
        int animals,
        int other
) {
    public int total() {
        return items + monsters + animals + other;
    }
}
