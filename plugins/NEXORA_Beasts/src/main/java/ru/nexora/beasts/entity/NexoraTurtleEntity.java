package ru.nexora.beasts.entity;

import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.passive.EntityTurtle;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public final class NexoraTurtleEntity extends EntityTurtle implements CustomEntity {
    public static final String IDENTIFIER = "nexora:beast_turtle";
    private static EntityDefinition definition;

    public NexoraTurtleEntity(FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }

    public static synchronized void register() {
        EntityDefinition existing = EntityManager.get().getDefinition(IDENTIFIER);
        if (existing != null) { definition = existing; return; }
        definition = EntityDefinition.builder().identifier(IDENTIFIER)
                .parentEntity("minecraft:turtle").spawnEgg(false)
                .alternateName("nexora_beast_turtle")
                .implementation(NexoraTurtleEntity.class).build();
        EntityManager.get().registerDefinition(definition);
    }

    @Override public EntityDefinition getEntityDefinition() {
        if (definition == null) throw new IllegalStateException("NEXORA turtle entity was not registered");
        return definition;
    }
    @Override public int getNetworkId() { return getEntityDefinition().getRuntimeId(); }
}
