package lv.lenc;

import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;

public class GlowingLabelWithBorder extends StackPane {

    private final GlowingLabel label;
    private final double fontSizeFullHD;
    private Tooltip fullTextTooltip;

    public GlowingLabelWithBorder(String text,
                                  double widthFullHD, double heightFullHD,
                                  double fontSizeFullHD) {
        this.label = new GlowingLabel(text);
        this.fontSizeFullHD = fontSizeFullHD;

        getStyleClass().add("glowing-label-border");
        setAlignment(Pos.CENTER);

        // === SIZE: design relative to 1920x1080 ===
        double w = UiScaleHelper.SCREEN_WIDTH * (widthFullHD / 1920.0);
        double h = UiScaleHelper.SCREEN_HEIGHT * (heightFullHD / 1080.0);

        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);

        // === font size scaling (
        applyTextStyle(text);
        label.setAlignment(Pos.CENTER);
        label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        label.setWrapText(true);

        getChildren().add(label);
        if (text != null && !text.isBlank()) {
            updateTooltip(text);
        }
    }

    //
    public GlowingLabelWithBorder(String text) {
        this(text, 220, 70, 17); // 
    }

    public void setText(String text) {
        label.setText(text);
        applyTextStyle(text);
        updateTooltip(text);
    }

    public GlowingLabel getLabel() {
        return label;
    }

    public void setSelected(boolean selected) {
        if (selected) {
            if (!getStyleClass().contains("glowing-label-border-selected"))
                getStyleClass().add("glowing-label-border-selected");
            label.setGlowOrange(true);
        } else {
            getStyleClass().remove("glowing-label-border-selected");
            label.setGlowOrange(false);
        }
    }

    private void applyTextStyle(String text) {
        int length = text == null ? 0 : text.codePointCount(0, text.length());
        double base = UiScaleHelper.scaleFont(fontSizeFullHD, 10.0);
        double factor = 1.0;
        if (length > 46) {
            factor = 0.70;
        } else if (length > 34) {
            factor = 0.78;
        } else if (length > 24) {
            factor = 0.88;
        }
        double fs = Math.max(UiScaleHelper.scaleFont(9.0, 8.5), base * factor);
        label.setStyle(
                "-fx-font-family: 'Arial Black';"
                        + "-fx-font-size: " + fs + "px;"
                        + "-fx-padding: "
                        + UiScaleHelper.scaleY(2.0) + " "
                        + UiScaleHelper.scaleX(8.0) + " "
                        + UiScaleHelper.scaleY(2.0) + " "
                        + UiScaleHelper.scaleX(8.0) + ";"
        );
    }

    private void updateTooltip(String text) {
        if (fullTextTooltip != null) {
            Tooltip.uninstall(this, fullTextTooltip);
            fullTextTooltip = null;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        fullTextTooltip = new Tooltip(text);
        Tooltip.install(this, fullTextTooltip);
    }
}
