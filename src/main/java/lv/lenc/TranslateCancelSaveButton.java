package lv.lenc;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;

public final class TranslateCancelSaveButton extends StackPane {

    public enum State { TRANSLATE, CANCEL, SAVE }

    private final LocalizationManager localization;
    private final MyButton button;

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.TRANSLATE);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private CompletableFuture<?> running;

    private Supplier<CompletableFuture<?>> translateStarter;
    private Runnable saveAction = () -> {};
    private Runnable cancelHook = () -> {};
    private Consumer<Throwable> errorHandler = Throwable::printStackTrace;

    private volatile Thread runningThread;
    private static final ExecutorService TRANSLATE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "translate-worker");
        t.setDaemon(true);
        // OPTIMIZED: Normal priority for equal translation speed across all languages
        // t.setPriority(Thread.MAX_PRIORITY);  // Removed: use normal priority
        return t;
    });

    // защита от старых completion
    private final AtomicLong runIdGen = new AtomicLong(0);
    private volatile long activeRunId = 0;

    public TranslateCancelSaveButton(LocalizationManager localization,
                                     String texturePath,
                                     boolean isGreen,
                                     double strengthGlow,
                                     double strengthGlowMAX) {

        this.localization = Objects.requireNonNull(localization, "localization");

        this.button = UIElementFactory.createCustomLongButton(
                localization.get("button.translate"),
                texturePath,
                isGreen,
                strengthGlow,
                strengthGlowMAX
        );

        button.disableProperty().bind(disableProperty());

        getChildren().add(button);
        StackPane.setAlignment(button, Pos.CENTER_RIGHT);
        setMaxWidth(Double.MAX_VALUE);

        state.addListener(this::onStateChanged);
        applyText(State.TRANSLATE);
        state.set(State.TRANSLATE);

        button.setOnAction(e -> handleClick());
    }
    public void setCustomText(String text) {
        button.setText(text);
    }
    public void clearCustomText() {
        applyText(state.get());
    }


    public static CompletableFuture<Void> runAsync(Runnable work, Consumer<Thread> onThread) {
        return CompletableFuture.runAsync(() -> {
            Thread t = Thread.currentThread();
            // OPTIMIZED: No priority boosting - equal treatment for all language translations
            onThread.accept(t);
            work.run();
        }, TRANSLATE_EXECUTOR);

    }
    private void onStateChanged(javafx.beans.value.ObservableValue<? extends State> obs, State oldS, State newS) {
        applyText(newS);
    }
    public void refreshText() {
        applyText(state.get());
    }

    public void resetToTranslateState() {
        cancelRequested.set(false);
        activeRunId = runIdGen.incrementAndGet();
        if (Platform.isFxApplicationThread()) {
            state.set(State.TRANSLATE);
        } else {
            Platform.runLater(() -> state.set(State.TRANSLATE));
        }
    }

    public void setTranslateStarter(Supplier<CompletableFuture<?>> starter) {
        this.translateStarter = Objects.requireNonNull(starter);
    }

    public void setSaveAction(Runnable saveAction) {
        this.saveAction = Objects.requireNonNull(saveAction);
    }

    public void setCancelHook(Runnable cancelHook) {
        this.cancelHook = Objects.requireNonNull(cancelHook);
    }

    public void setErrorHandler(Consumer<Throwable> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler);
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    private void applyText(State s) {
        switch (s) {
            case TRANSLATE -> button.setText(localization.get("button.translate"));
            case CANCEL -> button.setText(localization.get("button.translate.cancel"));
            case SAVE -> button.setText(localization.get("button.translate.save"));
        }
    }

    public void setRunningThread(Thread thread) {
        this.runningThread = thread;
    }

    private void handleClick() {
        switch (state.get()) {
            case TRANSLATE -> startTranslate();
            case CANCEL -> cancel();
            case SAVE -> save();
        }
    }

    private void startTranslate() {
        if (translateStarter == null) {
            errorHandler.accept(new IllegalStateException("Translate starter is not set"));
            return;
        }

        cancelRequested.set(false);
        long runId = runIdGen.incrementAndGet();
        activeRunId = runId;
        state.set(State.CANCEL);

        try {
            running = translateStarter.get();
            if (running == null) {
                if (activeRunId == runId) {
                    state.set(State.TRANSLATE);
                }
                errorHandler.accept(new IllegalStateException("Translate starter returned null future"));
                return;
            }

            running.whenComplete((ok, ex) -> Platform.runLater(() -> {
                if (activeRunId != runId) {
                    return; // старый completion игнорируем
                }

                if (cancelRequested.get()) {
                    state.set(State.TRANSLATE);
                    return;
                }

                if (ex != null) {
                    state.set(State.TRANSLATE);
                    errorHandler.accept(ex);
                    return;
                }

                state.set(State.SAVE);
            }));

        } catch (Throwable t) {
            if (activeRunId == runId) {
                state.set(State.TRANSLATE);
            }
            errorHandler.accept(t);
        }
    }

    public void requestCancel() {
        Platform.runLater(this::cancel);
    }

    private void save() {
        boolean saved = false;
        try {
            saveAction.run();
            saved = true;
        } catch (Throwable t) {
            errorHandler.accept(t);
        }

        state.set(saved ? State.TRANSLATE : State.SAVE);
    }

    private void cancel() {
        cancelRequested.set(true);
        activeRunId = runIdGen.incrementAndGet();

        try {
            cancelHook.run();
        } catch (Throwable ignored) {
        }

        if (runningThread != null) {
            runningThread.interrupt();
            runningThread = null;
        }

        if (running != null) {
            running.cancel(true);
            running = null;
        }

        state.set(State.TRANSLATE);
    }
}
