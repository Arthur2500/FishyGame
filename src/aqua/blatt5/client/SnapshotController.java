package aqua.blatt5.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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