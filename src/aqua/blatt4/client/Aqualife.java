package aqua.blatt4.client;

import javax.swing.SwingUtilities;

public class Aqualife {

    public static void main(String[] args) {
        ClientCommunicator communicator = new ClientCommunicator();
        TankModel tankModel = new TankModel(communicator);

        communicator.newClientReceiver(tankModel).start();

        SwingUtilities.invokeLater(new AquaGui(tankModel));

        tankModel.run();
    }
}