package com.viaupdater.model;

public class ViaPlugin {

    private final String name;
    private final String owner;
    private final String repo;
    private final String artifactId;

    public ViaPlugin(String name, String owner, String repo, String artifactId) {
        this.name = name;
        this.owner = owner;
        this.repo = repo;
        this.artifactId = artifactId;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepo() {
        return repo;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public static ViaPlugin fromName(String pluginName) {
        return switch (pluginName.toLowerCase()) {
            case "viaversion" -> new ViaPlugin("ViaVersion", "ViaVersion", "ViaVersion", "ViaVersion");
            case "viabackwards" -> new ViaPlugin("ViaBackwards", "ViaVersion", "ViaBackwards", "ViaBackwards");
            case "viarewind" -> new ViaPlugin("ViaRewind", "ViaVersion", "ViaRewind", "ViaRewind");
            case "viaaprilfools" -> new ViaPlugin("ViaAprilFools", "ViaVersion", "ViaAprilFools", "ViaAprilFools");
            default -> throw new IllegalArgumentException("未知的Via插件: " + pluginName);
        };
    }

    public static boolean isSupported(String pluginName) {
        try {
            fromName(pluginName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}