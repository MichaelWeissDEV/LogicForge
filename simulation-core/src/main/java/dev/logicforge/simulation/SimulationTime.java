package dev.logicforge.simulation;

/**
 * A value type for simulation time, representing physical time in picoseconds.
 *
 * <p>This is a simple wrapper around a {@code long} value that makes the unit explicit:
 * one unit of {@link SimulationTime} is one picosecond. This avoids any ambiguity about
 * whether a number represents nanoseconds, microseconds or some other unit.
 *
 * <p>Future extensions may add convenience methods for other units, but the canonical
 * representation is always picoseconds, stored as a {@code long} for precision and
 * to avoid any floating-point inaccuracies.
 *
 * <p>This type is immutable and thread-safe.
 */
public final class SimulationTime implements Comparable<SimulationTime> {

    /**
     * The number of picoseconds in one nanosecond.
     */
    public static final long PICOSECONDS_PER_NANOSECOND = 1_000L;

    /**
     * The number of picoseconds in one microsecond.
     */
    public static final long PICOSECONDS_PER_MICROSECOND = 1_000_000L;

    /**
     * The number of picoseconds in one millisecond.
     */
    public static final long PICOSECONDS_PER_MILLISECOND = 1_000_000_000L;

    /**
     * The number of picoseconds in one second.
     */
    public static final long PICOSECONDS_PER_SECOND = 1_000_000_000_000L;

    /** Zero time. */
    public static final SimulationTime ZERO = new SimulationTime(0);

    private final long picoseconds;

    /**
     * Creates a SimulationTime from a given number of picoseconds.
     *
     * @param picoseconds the time in picoseconds
     */
    public SimulationTime(long picoseconds) {
        this.picoseconds = picoseconds;
    }

    /**
     * Creates a SimulationTime from a given number of picoseconds.
     *
     * @param picoseconds the time in picoseconds
     * @return a new SimulationTime instance
     */
    public static SimulationTime ofPicoseconds(long picoseconds) {
        return new SimulationTime(picoseconds);
    }

    /**
     * Creates a SimulationTime from a given number of nanoseconds.
     *
     * @param nanoseconds the time in nanoseconds
     * @return a new SimulationTime instance
     */
    public static SimulationTime ofNanoseconds(long nanoseconds) {
        return new SimulationTime(nanoseconds * PICOSECONDS_PER_NANOSECOND);
    }

    /**
     * Creates a SimulationTime from a given number of microseconds.
     *
     * @param microseconds the time in microseconds
     * @return a new SimulationTime instance
     */
    public static SimulationTime ofMicroseconds(long microseconds) {
        return new SimulationTime(microseconds * PICOSECONDS_PER_MICROSECOND);
    }

    /**
     * Creates a SimulationTime from a given number of milliseconds.
     *
     * @param milliseconds the time in milliseconds
     * @return a new SimulationTime instance
     */
    public static SimulationTime ofMilliseconds(long milliseconds) {
        return new SimulationTime(milliseconds * PICOSECONDS_PER_MILLISECOND);
    }

    /**
     * Creates a SimulationTime from a given number of seconds.
     *
     * @param seconds the time in seconds
     * @return a new SimulationTime instance
     */
    public static SimulationTime ofSeconds(long seconds) {
        return new SimulationTime(seconds * PICOSECONDS_PER_SECOND);
    }

    /**
     * Returns the time value as picoseconds.
     *
     * @return the time in picoseconds
     */
    public long toPicoseconds() {
        return picoseconds;
    }

    /**
     * Returns the time value as nanoseconds (truncated).
     *
     * @return the time in nanoseconds
     */
    public long toNanoseconds() {
        return picoseconds / PICOSECONDS_PER_NANOSECOND;
    }

    /**
     * Returns the time value as microseconds (truncated).
     *
     * @return the time in microseconds
     */
    public long toMicroseconds() {
        return picoseconds / PICOSECONDS_PER_MICROSECOND;
    }

    /**
     * Returns the time value as milliseconds (truncated).
     *
     * @return the time in milliseconds
     */
    public long toMilliseconds() {
        return picoseconds / PICOSECONDS_PER_MILLISECOND;
    }

    /**
     * Returns the time value as seconds (truncated).
     *
     * @return the time in seconds
     */
    public long toSeconds() {
        return picoseconds / PICOSECONDS_PER_SECOND;
    }

    /**
     * Returns a new SimulationTime that is the sum of this time and another.
     *
     * @param other the time to add
     * @return the sum of the two times
     */
    public SimulationTime plus(SimulationTime other) {
        return new SimulationTime(this.picoseconds + other.picoseconds);
    }

    /**
     * Returns a new SimulationTime that is this time minus another.
     *
     * @param other the time to subtract
     * @return the difference between the two times
     */
    public SimulationTime minus(SimulationTime other) {
        return new SimulationTime(this.picoseconds - other.picoseconds);
    }

    /**
     * Returns true if this time is less than another.
     *
     * @param other the time to compare against
     * @return true if this time is less than the other
     */
    public boolean isLessThan(SimulationTime other) {
        return this.picoseconds < other.picoseconds;
    }

    /**
     * Returns true if this time is greater than another.
     *
     * @param other the time to compare against
     * @return true if this time is greater than the other
     */
    public boolean isGreaterThan(SimulationTime other) {
        return this.picoseconds > other.picoseconds;
    }

    /**
     * Returns true if this time is equal to another.
     *
     * @param other the time to compare against
     * @return true if this time is equal to the other
     */
    public boolean isEqualTo(SimulationTime other) {
        return this.picoseconds == other.picoseconds;
    }

    @Override
    public int compareTo(SimulationTime other) {
        return Long.compare(this.picoseconds, other.picoseconds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SimulationTime other = (SimulationTime) obj;
        return picoseconds == other.picoseconds;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(picoseconds);
    }

    @Override
    public String toString() {
        if (picoseconds == 0) {
            return "0 ps";
        }
        // For times >= 1 nanosecond, show in the most appropriate unit
        if (picoseconds % PICOSECONDS_PER_SECOND == 0) {
            return (picoseconds / PICOSECONDS_PER_SECOND) + " s";
        }
        if (picoseconds % PICOSECONDS_PER_MILLISECOND == 0) {
            return (picoseconds / PICOSECONDS_PER_MILLISECOND) + " ms";
        }
        if (picoseconds % PICOSECONDS_PER_MICROSECOND == 0) {
            return (picoseconds / PICOSECONDS_PER_MICROSECOND) + " us";
        }
        if (picoseconds % PICOSECONDS_PER_NANOSECOND == 0) {
            return (picoseconds / PICOSECONDS_PER_NANOSECOND) + " ns";
        }
        return picoseconds + " ps";
    }
}
