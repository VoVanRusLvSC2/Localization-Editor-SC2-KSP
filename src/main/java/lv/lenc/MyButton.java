package lv.lenc;

import javafx.scene.control.Button;

public class MyButton extends Button implements Disabable {
    public MyButton(String text) {
        super(text);
    }

    public void disable(Boolean bol)
    {
    if ( bol == true){
        this.disableProperty().set(true); // Set disabled property to true
    }
    else { this.disableProperty().set(false);}
    }
}
