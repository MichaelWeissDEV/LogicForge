package dev.logicforge.ui.edit;

import java.util.Optional;

/**
 * What is armed for click-to-place or being dragged out of the palette: either an ordinary
 * component or a physical chip package. Keeping these as two distinct types — rather than a
 * single string with an ad-hoc prefix — means a placement request can never be misread as
 * the wrong kind by code further down the pipeline.
 */
public sealed interface PlacementRequest {

    String CLIPBOARD_COMPONENT_PREFIX = "logicforge:component:";
    String CLIPBOARD_CHIP_PREFIX = "logicforge:chip:";

    record Component(String definitionId) implements PlacementRequest {
    }

    record Chip(String chipDefinitionId) implements PlacementRequest {
    }

    /** The encoding used to carry a placement request across a JavaFX drag-and-drop. */
    default String toClipboardString() {
        return switch (this) {
            case Component component -> CLIPBOARD_COMPONENT_PREFIX + component.definitionId();
            case Chip chip -> CLIPBOARD_CHIP_PREFIX + chip.chipDefinitionId();
        };
    }

    static Optional<PlacementRequest> fromClipboardString(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw.startsWith(CLIPBOARD_COMPONENT_PREFIX)) {
            return Optional.of(new Component(raw.substring(CLIPBOARD_COMPONENT_PREFIX.length())));
        }
        if (raw.startsWith(CLIPBOARD_CHIP_PREFIX)) {
            return Optional.of(new Chip(raw.substring(CLIPBOARD_CHIP_PREFIX.length())));
        }
        return Optional.empty();
    }
}
