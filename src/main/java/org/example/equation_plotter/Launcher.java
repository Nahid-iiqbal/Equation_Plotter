package org.example.equation_plotter;

import javafx.application.Application;
import org.mariuszgromada.math.mxparser.License;
import org.mariuszgromada.math.mxparser.mXparser;

public class Launcher {
    public static void main(String[] args) {
        System.setProperty("prism.order", "d3d,es2,sw");
        System.setProperty("prism.verbose", "true");
        /* Non-Commercial Use Confirmation */
        boolean isCallSuccessful = License.iConfirmNonCommercialUse("NahidIqbal");

        /* Verification if use type has been already confirmed */
        boolean isConfirmed = License.checkIfUseTypeConfirmed();

        /* Checking use type confirmation message */
        String message = License.getUseTypeConfirmationMessage();

        /* ----------- */
        mXparser.consolePrintln("isCallSuccessful = " + isCallSuccessful);
        mXparser.consolePrintln("isConfirmed = " + isConfirmed);
        mXparser.consolePrintln("message = " + message);
        Application.launch(Equator.class, args);
    }
}
// random comment just for testing git push and pull