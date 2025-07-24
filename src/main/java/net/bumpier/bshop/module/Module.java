package net.bumpier.bshop.module;

public interface Module {
    void onEnable();
    void onDisable();
    String getName();
}