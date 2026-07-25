package com.fontainerepublic.core;

public interface IModule {
    String getName();
    void init();
    void shutdown();

    default int getPriority() {
        return 50;
    }
}
