package Backend;

import android.content.Context;

public class Controller {
    public static Controller instance;
    Model model;

    private Controller() { }

    private void initController() {
        model = new Model();
    }

    public static void createInstance() {
        instance = new Controller();
        instance.initController();
    }

    public Model getModel() { return model; }
}
