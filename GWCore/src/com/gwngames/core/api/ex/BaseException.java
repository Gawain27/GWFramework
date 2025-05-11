package com.gwngames.core.api.ex;

import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class of all exceptions
 * TODO: language support
 * @author samlam
 */
public class BaseException extends Exception {
    protected static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$(\\d+)");
    private final int errorCode;
    protected final String[] params;

    public BaseException(int code, String ...params){
        this.errorCode = code;
        this.params = params;
    }

    public int getErrorCode(){
        return errorCode;
    }

    /** Fills in the paramters into a message template.
        * A message template contains numerated placeholders for the parameters.
        * Placeholders consists of a dollar character and a number that corresponds
     * to the one-based index of the parameter. The placeholders must be
     * separated by white space from other character. Non-existing parameters
     * are replaced by the empty string.
        * <p>
        * Example:
        * b = new BusinessException(999, "INSERT", "unique constraint");
     * b.format("$1 violated $2 which cost me $ 2.50")
         * =&gt; "INSERT violated unique constraint which cost me $ 2.50"
        *
        * @param template the message template
     * @return the formatted message
     */
    public String format(String template) {
        // Log is not serializable, so don't make it an instance variable
        FileLogger log = FileLogger.get(LogFiles.ERROR);
        int maxLength = template.length();
        if (params != null) {
            for (String param : params) {
                if (param == null) continue;
                maxLength += param.length();
            }
        }

        Matcher m = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder message = new StringBuilder(maxLength);
        while (m.find()) {
            String index = template.substring(m.start(1), m.end(1));
            String replacement = "";
            if ("$".equals(index)) {
                replacement = "$";
            } else if (params == null) {
                log.error("no parameter for placeholder $"+ index +" with code "+ errorCode);
            } else {
                try {
                    int paramIndex = Integer.parseInt(index) - 1;
                    replacement = params[paramIndex];
                    if (replacement == null) {
                        log.error("parameter for placeholder $"+ index +" is null with code "+ errorCode);
                        replacement = "";
                    }
                } catch(NumberFormatException e) {
                    log.error("non numeric placeholder: $"+ index +" with code "+ errorCode);
                } catch(ArrayIndexOutOfBoundsException e) {
                    log.error("no parameter for placeholder $"+ index +" with code "+ errorCode);
                }
            }
            assert replacement != null;
            m.appendReplacement(message, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(message);
        return message.toString();
    }

    @Override
    public String getMessage() {
        return toString();
    }

    /**
     * Lists all parameters of this exception.
     * Subclasses should override this method and produce a nicer message
     * that is suitable for logs.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(255);
        sb.append(getClass().getName());
        sb.append(" with code ");
        sb.append(errorCode);
        sb.append(" and params ");
        for (String param : params) {
            sb.append(param);
            sb.append(", ");
        }
        return sb.toString();
    }
}
