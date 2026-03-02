//package lv.lenc;
//import javafx.application.Platform;
//import javafx.scene.Scene;
//import javafx.stage.Stage;
//
//public class BackgroundGridOpener {
//
//    public static void openInNewThreadedWindow() {
//        new Thread(() -> {
//            // 
//            try {
//                Thread.sleep(1000); // 
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//
//            // 
//            Platform.runLater(() -> {
//                BackgroundGridLayer layer = new BackgroundGridLayer();
//                Scene scene = new Scene(layer, 1280, 720); // 
//                Stage stage = new Stage();
//                stage.setScene(scene);
//                stage.setTitle("Background Grid Layer — Separate Window");
//                stage.show();
//            });
//        }).start();
//    }
//}
