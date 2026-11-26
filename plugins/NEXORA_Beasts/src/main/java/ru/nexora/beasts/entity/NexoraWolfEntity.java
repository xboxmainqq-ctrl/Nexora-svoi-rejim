package ru.nexora.beasts.entity;

import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.mob.EntityWolf;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * Server-side NEXORA wolf companion.
 *
 * Its custom network identifier is paired with the NEXORA Bedrock resource pack.
 * The vanilla wolf implementation is deliberately reused for safe movement and
 * combat compatibility while the client renders the custom NEXORA geometry.
 */
public final class NexoraWolfEntity extends EntityWolf implements CustomEntity {
    public static final String IDENTIFIER = "nexora:beast_wolf";
    private static EntityDefinition definition;

    public NexoraWolfEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public static synchronized void register() {
        EntityDefinition existing = EntityManager.get().getDefinition(IDENTIFIER);
        if (existing != null) {
            definition = existing;
            return;
        }

        EntityDefinition created = EntityDefinition.builder()
                .identifier(IDENTIFIER)
                .parentEntity("minecraft:wolf")
                .spawnEgg(false)
                .alternateName("nexora_beast_wolf")
                .implementation(NexoraWolfEntity.class)
                .build();
        EntityManager.get().registerDefinition(created);
        definition = created;
    }

    @Override
    public EntityDefinition getEntityDefinition() {
        if (definition == null) {
            throw new IllegalStateException("NEXORA wolf entity was not registered");
        }
        return definition;
    }

    @Override
    public int getNetworkId() {
        return getEntityDefinition().getRuntimeId();
    }
}
