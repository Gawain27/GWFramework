package com.gwngames.core.api.base.cfg;

public interface IApplicationLogger {

    /* ------------------------------------------------------------ */
    /*  LibGDX-style API (tag-based)                                */
    /* ------------------------------------------------------------ */
    void log(String tag, String msg);
    void log(String tag, String msg, Throwable ex);

    void error(String tag, String msg);
    void error(String tag, String msg, Throwable ex);

    void debug(String tag, String msg);
    void debug(String tag, String msg, Throwable ex);

    /* ------------------------------------------------------------ */
    /*  File-targeted + formatting API                              */
    /*  - `msg` can contain "{}" placeholders, filled by args       */
    /* ------------------------------------------------------------ */
    void log(String file, String msg, Object... args);
    void log(String file, String msg, Throwable ex, Object... args);

    void error(String file, String msg, Object... args);
    void error(String file, String msg, Throwable ex, Object... args);

    void debug(String file, String msg, Object... args);
    void debug(String file, String msg, Throwable ex, Object... args);
}
