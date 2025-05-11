package com.gwngames.core.api.ex;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.List;

/**
 * A runtime exception that immediately shows a Swing error dialog
 * when it is created/thrown.
 *
 * <p>Usage example:
 * <pre>
 *     if (amount < 0) {
 *         throw new ErrorPopupException("Amount cannot be negative!");
 *     }
 * </pre>
 */
public class ErrorPopupException extends BaseException {

    public ErrorPopupException(String message) {
        super(ExceptionCode.generate(ErrorPopupException.class, List.of(ExceptionCode.DECA)), message);
        showErrorDialog(message);
    }

    /** Opens an ERROR_MESSAGE dialog on the Swing EDT. */
    private void showErrorDialog(String message) {
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(
                null,                       // parent component
                message,                    // dialog content
                "Error",                    // title
                JOptionPane.ERROR_MESSAGE   // icon / type
            )
        );
    }
}

