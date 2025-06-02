package com.gwngames.core.api.ex;

import com.gwngames.core.api.build.ITranslatable;
import com.gwngames.core.base.cfg.i18n.BasicTranslation;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.Locale;

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

    public ErrorPopupException(ITranslatable message, String ...params) {
        super(message, ExceptionCode.SYSTEM_FAULT, params);
        showErrorDialog(getMessage());
    }

    /** Opens an ERROR_MESSAGE dialog on the Swing EDT. */
    private void showErrorDialog(String message) {
        String errorTitle;
        try {
            errorTitle = translator.tr(BasicTranslation.ERROR.getKey(), Locale.getDefault());
        } catch (Exception e){
            errorTitle = BasicTranslation.ERROR.getDefaultCaption();
        }
        String finalErrorTitle = errorTitle;
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(
                null,                       // parent component
                message,                    // dialog content
                finalErrorTitle,                    // title
                JOptionPane.ERROR_MESSAGE   // icon / type
            )
        );
    }
}

