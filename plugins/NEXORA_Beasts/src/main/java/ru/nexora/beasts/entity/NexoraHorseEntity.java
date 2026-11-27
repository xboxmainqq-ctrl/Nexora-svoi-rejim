package ru.nexora.beasts.entity;

import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.passive.EntityHorse;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public final class NexoraHorseEntity extends EntityHorse implements CustomEntity {
    public static final String IDENTIFIER = "nexora:beast_horse";
    private static EntityDefinition definition;

    public NexoraHorseEntity(FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }

    public static synchronized void register() {
        EntityDefinition existing = EntityManager.get().getDefinition(IDENTIFIER);
        if (existing != null) { definition = existing; return; }
        definition = EntityDefinition.builder().identifier(IDENTIFIER)
                .parentEntity("minecraft:horse").spawnEgg(false)
                .alternateName("nexora_horse")
                .implementation(NexoraHorseEntity.class).build();
        EntityManager.get().registerDefinition(definition);
    }

    @Override public EntityDefinition getEntityDefinition() {
        if (definition == null) throw new IllegalStateException("NEXORA horse entity was not registered");
        return definition;
    }
    @Override public int getNetworkId() { return getEntityDefinition().getRuntimeId(); }
}
