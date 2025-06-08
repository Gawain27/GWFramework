package com.gwngames.core.api.ex;

import com.gwngames.core.api.base.ILocale;
import com.gwngames.core.api.base.ITranslationService;
import com.gwngames.core.api.build.ITranslatable;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.util.Cdi;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class of all exceptions, with support for translation
 * and parameterized messages.
 *
 * @author samlam
 */
public class BaseException extends Exception {
    private static final FileLogger log = FileLogger.get(LogFiles.ERROR);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$(\\d+)");

    private final ExceptionCode errorCode;
    private final ITranslatable errorKey;
    protected final String[] params;

    @Inject
    protected ITranslationService translator;

    @Inject
    protected ILocale locale;

    public BaseException(ITranslatable errorKey, ExceptionCode code, String... params) {
        // Inject translation service before anything else
        Cdi.inject(this);

        this.errorCode = code;
        this.errorKey = errorKey;
        this.params = params;
    }

    public ExceptionCode getErrorCode() {
        return errorCode;
    }

    /**
     * Fills in the parameters into a message template.
     * A message template contains numerated placeholders for the parameters.
     * Placeholders consist of a dollar character and a number that corresponds
     * to the one-based index of the parameter. The placeholders must be
     * separated by white space from other characters. Non-existing parameters
     * are replaced by the empty string.
     * Example:
     *   format("$1 violated $2 which cost me $ 2.50")
     *   ⇒ "INSERT violated unique constraint which cost me $ 2.50"
     *
     * @param template the message template
     * @return the formatted message
     */
    public String format(String template) {
        FileLogger log = FileLogger.get(LogFiles.ERROR);

        int maxLength = (template == null ? 0 : template.length());
        if (params != null) {
            for (String param : params) {
                if (param != null) {
                    maxLength += param.length();
                }
            }
        }

        Matcher m = PLACEHOLDER_PATTERN.matcher(template == null ? "" : template);
        StringBuilder message = new StringBuilder(maxLength);

        while (m.find()) {
            assert template != null;
            String index = template.substring(m.start(1), m.end(1));
            String replacement = "";

            if ("$".equals(index)) {
                // literal dollar
                replacement = "$";
            } else if (params == null) {
                log.error("No parameter for placeholder $" + index + " with code " + errorCode);
            } else {
                try {
                    int paramIndex = Integer.parseInt(index) - 1;
                    replacement = params[paramIndex];
                    if (replacement == null) {
                        log.error("Parameter for placeholder $" + index + " is null with code " + errorCode);
                        replacement = "";
                    }
                } catch (NumberFormatException e) {
                    log.error("Non-numeric placeholder: $" + index + " with code " + errorCode, e);
                } catch (ArrayIndexOutOfBoundsException e) {
                    log.error("No parameter for placeholder $" + index + " with code " + errorCode, e);
                }
            }

            assert replacement != null;
            m.appendReplacement(message, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(message);
        return message.toString();
    }

    protected String getTranslatedText(ITranslatable translatable){
        return translator.tr(translatable.getKey(), locale.getLocale());
    }
    /**
     * Returns the translated (or default) message and then applies parameter formatting.
     */
    @Override
    public String getMessage() {
        String errorMessage;

        if (translator == null) {
            // Injection failed or translator not available
            log.error("Translation service unavailable for key \"" + errorKey.getKey() + "\". Using default caption.");
            errorMessage = errorKey.getDefaultCaption();
        } else {
            try {
                errorMessage = translator.tr(errorKey.getKey(), locale.getLocale());
                if (errorMessage == null) {
                    // translator.tr might return null if key is missing
                    FileLogger.get(LogFiles.ERROR)
                        .error("Translation missing for key \"" + errorKey.getKey() + "\". Using default caption.");
                    errorMessage = errorKey.getDefaultCaption();
                }
            } catch (Exception e) {
                FileLogger.get(LogFiles.ERROR)
                    .error("Error translating key \"" + errorKey.getKey() + "\". Using default caption.", e);
                errorMessage = errorKey.getDefaultCaption();
            }
        }

        return format(errorMessage);
    }

    /**
     * Subclasses can override to produce a more detailed log-friendly message,
     * but by default we include class name, error code, and parameters.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(255);
        sb.append(getClass().getName());
        sb.append(" with code ");
        sb.append(errorCode);
        sb.append(" and params ");
        if (params != null) {
            for (String param : params) {
                sb.append(param);
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
