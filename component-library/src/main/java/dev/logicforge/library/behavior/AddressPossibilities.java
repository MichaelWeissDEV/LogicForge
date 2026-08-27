package dev.logicforge.library.behavior;

import dev.logicforge.logic.LogicOperations;
import dev.logicforge.logic.LogicVector;
import java.util.Arrays;

/** Bounded expansion of a four-state address bus into possible concrete word addresses. */
final class AddressPossibilities {
    static final int DEFAULT_EXPLOSION_THRESHOLD = 4_096;

    private final int wordCount;
    private final int[] addresses;
    private final boolean allAddresses;

    private AddressPossibilities(int wordCount, int[] addresses, boolean allAddresses) {
        this.wordCount = wordCount;
        this.addresses = addresses;
        this.allAddresses = allAddresses;
    }

    static AddressPossibilities resolve(LogicVector rawAddress, int wordCount) {
        return resolve(rawAddress, wordCount, DEFAULT_EXPLOSION_THRESHOLD);
    }

    static AddressPossibilities resolve(LogicVector rawAddress, int wordCount, int threshold) {
        if (wordCount < 1 || threshold < 1) {
            throw new IllegalArgumentException("Word count and threshold must be positive");
        }
        LogicVector address = LogicOperations.asGateInput(rawAddress);
        int unknownBits = 0;
        for (int bit = 0; bit < address.width(); bit++) {
            if (!address.getBit(bit).isDefined()) {
                unknownBits++;
            }
        }
        long combinations = unknownBits >= Long.SIZE - 1 ? Long.MAX_VALUE : 1L << unknownBits;
        if (combinations > threshold) {
            return new AddressPossibilities(wordCount, new int[0], true);
        }
        int encodedCount = address.width() >= 31 ? Integer.MAX_VALUE : 1 << address.width();
        int[] candidates = new int[(int) Math.min(combinations, wordCount)];
        int count = 0;
        for (int candidate = 0; candidate < encodedCount && candidate < wordCount; candidate++) {
            if (StatefulControlPolicy.isPossible(address, candidate)) {
                if (count == candidates.length) {
                    candidates = Arrays.copyOf(candidates, Math.max(1, count * 2));
                }
                candidates[count++] = candidate;
            }
        }
        return new AddressPossibilities(wordCount, Arrays.copyOf(candidates, count), false);
    }

    boolean contains(int address) {
        if (address < 0 || address >= wordCount) {
            return false;
        }
        return allAddresses || Arrays.binarySearch(addresses, address) >= 0;
    }

    boolean isCertain(int address) {
        return !allAddresses && addresses.length == 1 && addresses[0] == address;
    }

    boolean isSingle() {
        return !allAddresses && addresses.length == 1;
    }

    int singleAddress() {
        if (!isSingle()) {
            throw new IllegalStateException("Address is not uniquely determined");
        }
        return addresses[0];
    }

    int possibleCount() {
        return allAddresses ? wordCount : addresses.length;
    }

    boolean coversAllAddresses() {
        return allAddresses;
    }

    void forEach(java.util.function.IntConsumer consumer) {
        if (allAddresses) {
            for (int address = 0; address < wordCount; address++) {
                consumer.accept(address);
            }
        } else {
            Arrays.stream(addresses).forEach(consumer);
        }
    }
}
