package aqua.blatt7.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ToggleController implements ActionListener {
    private final TankModel model;
    private final String fishId;

    public ToggleController(TankModel model, String fishid) {
        this.model = model;
        this.fishId = fishid;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (fishId != null && !fishId.isBlank()) {
            model.locateFishGlobally(fishId);
        }
    }
}