package com.gwngames.core.data;


/**
 * List of components name for override or other purposes
 * */
public enum ModuleNames {
    // Special
    UNIMPLEMENTED("Unimplemented", 0),
    INTERFACE("Interface", 1),

    // Modules
    CORE("Core", 5),
    GAME2D("2DGame", 15),

    // Project Modules
    NEEDLE_OF_SILVER("NeedleOfSilver", 100),

    // Starter
    STARTER("Starter", 1000)
    ;

    public final String moduleName;
    public final int modulePriority;

    ModuleNames(String moduleName, int modulePriority) {
        this.moduleName = moduleName;
        this.modulePriority = modulePriority;
    }

    public String getName(){
        return moduleName;
    }

    public int getPriority(){
        return modulePriority;
    }
}
