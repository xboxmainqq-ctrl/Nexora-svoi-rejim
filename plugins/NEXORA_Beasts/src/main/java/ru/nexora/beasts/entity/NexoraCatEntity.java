package ru.nexora.beasts.entity;

import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.passive.EntityCat;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public final class NexoraCatEntity extends EntityCat implements CustomEntity {
    public static final String IDENTIFIER = "nexora:beast_cat";
    private static EntityDefinition definition;

    public NexoraCatEntity(FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }

    public static synchronized void register() {
        EntityDefinition existing = EntityManager.get().getDefinition(IDENTIFIER);
        if (existing != null) { definition = existing; return; }
        EntityDefinition created = EntityDefinition.builder()
                .identifier(IDENTIFIER)
                .parentEntity("minecraft:cat")
                .spawnEgg(false)
                .alternateName("nexora_beast_cat")
                .implementation(NexoraCatEntity.class)
                .build();
        EntityManager.get().registerDefinition(created);
        definition = created;
    }

    @Override public EntityDefinition getEntityDefinition() {
        if (definition == null) throw new IllegalStateException("NEXORA cat entity was not registered");
        return definition;
    }

    @Override public int getNetworkId() { return getEntityDefinition().getRuntimeId(); }
}
