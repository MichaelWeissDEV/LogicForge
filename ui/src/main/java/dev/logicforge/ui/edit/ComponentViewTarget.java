package dev.logicforge.ui.edit;

import java.util.Optional;
import java.util.UUID;

/**
 * Everything needed to keep addressing one component after the editor has navigated
 * elsewhere: which circuit definition owns it, which concrete hierarchy instance is live
 * (if any), and its local component id.
 *
 * <p>A hierarchy instance path alone is not enough for document-backed operations — editing
 * a project-backed ROM's contents parameter needs the actual definition document it lives
 * on, which is a different thing from "whatever the editor currently has open". A widget
 * that outlives one moment of navigation, such as {@link dev.logicforge.ui.view.MemoryView},
 * should capture a {@code ComponentViewTarget} (via {@link CircuitEditor#viewTarget}) once
 * at open time and use it for every subsequent call, rather than re-deriving anything from
 * the editor's ambient current circuit/document.
 */
public record ComponentViewTarget(String circuitName, Optional<String> instancePath, UUID componentId) {

    public ComponentViewTarget {
        instancePath = instancePath == null ? Optional.empty() : instancePath;
    }
}
