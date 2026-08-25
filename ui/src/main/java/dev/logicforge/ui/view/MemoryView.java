package dev.logicforge.ui.view;

import dev.logicforge.logic.LogicState;
import dev.logicforge.logic.LogicVector;
import dev.logicforge.simulation.MemorySnapshot;
import dev.logicforge.ui.edit.CircuitEditor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.io.IOException;
import java.nio.file.Files;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

/** A window displaying the contents of a RAM or ROM component. */
public final class MemoryView extends Stage {

    private static final int PAGE_SIZE = 256;

    private final CircuitEditor editor;
    private final UUID componentId;
    private final TableView<MemoryRow> table = new TableView<>();
    private final ObservableList<MemoryRow> rows = FXCollections.observableArrayList();
    private final TextField jumpField = new TextField();
    private final Label pageLabel = new Label();
    private final Runnable editorListener = this::refresh;
    
    private int dataWidth = 8; // default, updated from snapshot
    private int memorySize;
    private int pageStart;
    private long lastRevision = Long.MIN_VALUE;
    private int lastReadAddress = -1;
    private int lastWriteAddress = -1;

    public MemoryView(CircuitEditor editor, UUID componentId, String title) {
        this.editor = editor;
        this.componentId = componentId;
        setTitle("Memory: " + title);
        
        TableColumn<MemoryRow, String> addrCol = new TableColumn<>("Address");
        addrCol.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().addrHex()));
        addrCol.setPrefWidth(80);
        addrCol.setEditable(false);
        
        TableColumn<MemoryRow, String> hexCol = new TableColumn<>("Hex");
        hexCol.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().hexValue()));
        hexCol.setPrefWidth(100);
        hexCol.setEditable(true);
        hexCol.setCellFactory(TextFieldTableCell.forTableColumn());
        hexCol.setOnEditCommit(event -> {
            String text = event.getNewValue().trim();
            int address = event.getRowValue().address();
            try {
                long val = Long.parseUnsignedLong(text, 16);
                LogicVector value = LogicVector.fromUnsignedLong(val, dataWidth);
                editor.writeMemoryWord(componentId, address, value);
            } catch (NumberFormatException e) {
                // Ignore invalid input
            }
            refresh();
        });
        
        TableColumn<MemoryRow, String> binCol = new TableColumn<>("Binary");
        binCol.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().binValue()));
        binCol.setPrefWidth(120);
        binCol.setEditable(false);
        
        table.setItems(rows);
        table.getColumns().addAll(addrCol, hexCol, binCol);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(ignored -> new TableRow<>() {
            @Override protected void updateItem(MemoryRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setStyle("");
                } else if (row.address() == lastWriteAddress) {
                    setStyle("-fx-background-color: rgba(255, 170, 60, 0.25);");
                } else if (row.address() == lastReadAddress) {
                    setStyle("-fx-background-color: rgba(80, 160, 255, 0.20);");
                } else {
                    setStyle("");
                }
            }
        });
        
        Label jumpLabel = new Label("Jump to:");
        jumpField.setPromptText("hex address");
        jumpField.setPrefWidth(100);
        jumpField.setOnAction(e -> jumpToAddress());
        Button jumpBtn = new Button("Go");
        jumpBtn.setOnAction(e -> jumpToAddress());
        Button previous = new Button("Previous Page");
        previous.setOnAction(e -> changePage(-PAGE_SIZE));
        Button next = new Button("Next Page");
        next.setOnAction(e -> changePage(PAGE_SIZE));
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refresh(true));
        Button loadBtn = new Button("Load .bin");
        loadBtn.setOnAction(e -> loadBinary());
        Button saveBtn = new Button("Save .bin");
        saveBtn.setOnAction(e -> saveBinary());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox jumpBar = new HBox(4, previous, next, pageLabel, jumpLabel, jumpField, jumpBtn,
                refreshButton, spacer, loadBtn, saveBtn);
        jumpBar.setPadding(new Insets(4));
        
        VBox content = new VBox(jumpBar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        
        Scene scene = new Scene(content, 420, 500);
        setScene(scene);
        
        editor.addChangeListener(editorListener);
        setOnHidden(event -> editor.removeChangeListener(editorListener));
        refresh(true);
    }
    
    private void refresh() {
        refresh(false);
    }

    private void refresh(boolean force) {
        long revision = editor.memoryRevision(componentId);
        if (!force && revision >= 0 && revision == lastRevision) {
            return;
        }
        Optional<MemorySnapshot> snap = editor.memorySnapshot(componentId);
        if (snap.isEmpty()) {
            rows.clear();
            memorySize = 0;
            return;
        }
        MemorySnapshot snapshot = snap.get();
        lastRevision = snapshot.revision();
        dataWidth = snapshot.wordWidth();
        memorySize = snapshot.size();
        lastReadAddress = snapshot.lastReadAddress();
        lastWriteAddress = snapshot.lastWriteAddress();
        pageStart = Math.max(0, Math.min(pageStart,
                Math.max(0, ((memorySize - 1) / PAGE_SIZE) * PAGE_SIZE)));
        
        int addrWidth = Math.max(1, Integer.toHexString(Math.max(0, snapshot.size() - 1)).length());
        
        int pageEnd = Math.min(snapshot.size(), pageStart + PAGE_SIZE);
        List<MemoryRow> newRows = new ArrayList<>(Math.max(0, pageEnd - pageStart));
        for (int i = pageStart; i < pageEnd; i++) {
            newRows.add(new MemoryRow(i, addrWidth, snapshot.wordAt(i), dataWidth));
        }
        rows.setAll(newRows);
        pageLabel.setText(memorySize == 0 ? "Empty"
                : String.format("%X–%X / %X", pageStart, Math.max(pageStart, pageEnd - 1), memorySize - 1));
        table.refresh();
    }

    private void changePage(int delta) {
        int target = Math.max(0, Math.min(pageStart + delta,
                Math.max(0, ((memorySize - 1) / PAGE_SIZE) * PAGE_SIZE)));
        if (target != pageStart) {
            pageStart = target;
            refresh(true);
        }
    }
    
    private void jumpToAddress() {
        String text = jumpField.getText().trim();
        try {
            int addr = Integer.parseUnsignedInt(text, 16);
            if (addr >= 0 && addr < memorySize) {
                pageStart = (addr / PAGE_SIZE) * PAGE_SIZE;
                refresh(true);
                int row = addr - pageStart;
                table.scrollTo(row);
                table.getSelectionModel().select(row);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
    }

    private void loadBinary() {
        FileChooser chooser = binaryChooser("Load memory image");
        java.io.File file = chooser.showOpenDialog(this);
        if (file == null) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            int bytesPerWord = (dataWidth + 7) / 8;
            int wordCount = Math.min(memorySize, (bytes.length + bytesPerWord - 1) / bytesPerWord);
            List<LogicVector> words = new ArrayList<>(wordCount);
            for (int word = 0; word < wordCount; word++) {
                long value = 0;
                for (int offset = 0; offset < bytesPerWord; offset++) {
                    int index = word * bytesPerWord + offset;
                    value = (value << 8) | (index < bytes.length ? bytes[index] & 0xffL : 0L);
                }
                words.add(LogicVector.fromUnsignedLong(value, dataWidth));
            }
            editor.loadMemory(componentId, words);
            refresh();
        } catch (IOException failure) {
            showIoError("Could not load memory image", failure);
        }
    }

    private void saveBinary() {
        Optional<MemorySnapshot> snapshot = editor.memorySnapshot(componentId);
        if (snapshot.isEmpty()) return;
        FileChooser chooser = binaryChooser("Save memory image");
        java.io.File file = chooser.showSaveDialog(this);
        if (file == null) return;
        try {
            int bytesPerWord = (dataWidth + 7) / 8;
            byte[] bytes = new byte[snapshot.get().size() * bytesPerWord];
            for (int word = 0; word < snapshot.get().size(); word++) {
                long value = snapshot.get().wordAt(word).toUnsignedLong().orElse(0L);
                for (int offset = bytesPerWord - 1; offset >= 0; offset--) {
                    bytes[word * bytesPerWord + offset] = (byte) value;
                    value >>>= 8;
                }
            }
            Files.write(file.toPath(), bytes);
        } catch (IOException failure) {
            showIoError("Could not save memory image", failure);
        }
    }

    private FileChooser binaryChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Raw binary (*.bin)", "*.bin"));
        return chooser;
    }

    private void showIoError(String title, IOException failure) {
        Alert alert = new Alert(Alert.AlertType.ERROR, failure.getMessage(), ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(this);
        alert.showAndWait();
    }
    
    public record MemoryRow(int address, int addrHexWidth, LogicVector value, int dataWidthBits) {
        public String addrHex() {
            return String.format("%0" + addrHexWidth + "X", address);
        }
        public String hexValue() {
            StringBuilder hex = new StringBuilder();
            int hexDigits = (dataWidthBits + 3) / 4;
            for (int d = hexDigits - 1; d >= 0; d--) {
                int nibble = 0;
                boolean hasUnknown = false;
                for (int b = 0; b < 4; b++) {
                    int bitIndex = d * 4 + b;
                    if (bitIndex >= dataWidthBits) continue;
                    LogicState state = value.getBit(bitIndex);
                    if (state == LogicState.UNKNOWN || state == LogicState.HIGH_IMPEDANCE) {
                        hasUnknown = true;
                        break;
                    }
                    if (state == LogicState.ONE) nibble |= (1 << b);
                }
                hex.append(hasUnknown ? '?' : Character.toUpperCase(Character.forDigit(nibble, 16)));
            }
            return hex.toString();
        }
        public String binValue() {
            StringBuilder bin = new StringBuilder();
            for (int i = dataWidthBits - 1; i >= 0; i--) {
                bin.append(switch (value.getBit(i)) {
                    case ZERO -> '0';
                    case ONE -> '1';
                    case UNKNOWN -> 'X';
                    case HIGH_IMPEDANCE -> 'Z';
                });
                if (i > 0 && i % 4 == 0) bin.append('_');
            }
            return bin.toString();
        }
    }
}
