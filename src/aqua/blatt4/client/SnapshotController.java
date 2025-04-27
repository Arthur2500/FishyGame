package aqua.blatt4.client;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * Controller to trigger a distributed snapshot when the menu item is selected.
 */
public class SnapshotController implements ActionListener {
    private final TankModel tankModel;

    public SnapshotController(TankModel tankModel) {
        this.tankModel = tankModel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        tankModel.initiateSnapshot();
    }
}