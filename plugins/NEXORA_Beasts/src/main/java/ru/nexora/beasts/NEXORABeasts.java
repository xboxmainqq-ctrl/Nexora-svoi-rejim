package ru.nexora.beasts;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerInteractEntityEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.ParticleEffect;
import cn.nukkit.level.Sound;
import cn.nukkit.level.particle.FlameParticle;
import cn.nukkit.level.particle.DustParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementButtonImageData;
import cn.nukkit.form.element.ElementDropdown;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowModal;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import ru.nexora.beasts.entity.NexoraWolfEntity;
import ru.nexora.beasts.entity.NexoraFoxEntity;
import ru.nexora.beasts.entity.NexoraCatEntity;
import ru.nexora.beasts.entity.NexoraBeeEntity;
import ru.nexora.beasts.entity.NexoraAxolotlEntity;
import ru.nexora.beasts.entity.NexoraHorseEntity;
import ru.nexora.beasts.entity.NexoraPandaEntity;
import ru.nexora.beasts.entity.NexoraPolarBearEntity;
import ru.nexora.beasts.entity.NexoraRabbitEntity;
import ru.nexora.beasts.entity.NexoraOcelotEntity;
import ru.nexora.beasts.entity.NexoraTurtleEntity;
import ru.nexora.beasts.entity.NexoraDolphinEntity;
import ru.nexora.beasts.entity.NexoraParrotEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class NEXORABeasts extends PluginBase implements Listener {
    private static final String ORB_NAME = "§bЛовчая сфера NEXORA";
    private static final String EGG_NAME = "§6Яйцо NEXORA";
    private static final String EGG_PREFIX = "§6Яйцо NEXORA · ";
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private static final String[] RARITIES = {
            "common", "uncommon", "rare", "epic", "legendary",
            "mythic", "ancient", "divine", "forbidden", "absolute"
    };
    private static final String[] RARITY_NAMES = {
            "Обычное", "Необычное", "Редкое", "Эпическое", "Легендарное",
            "Мифическое", "Древнее", "Божественное", "Запретное", "Абсолютное"
    };
    private static final String[] RARITY_COLORS = {
            "§7", "§a", "§b", "§5", "§6", "§d", "§3", "§e", "§c", "§f"
    };
    private static final int[] WILD_RARITY_WEIGHTS = {500, 220, 130, 70, 35, 20, 12, 7, 4, 2};
    private static final String[] RANDOM_SPECIES = {
            "Wolf", "Fox", "Rabbit", "Cat", "Bee", "Axolotl", "Horse",
            "Panda", "PolarBear", "Ocelot", "Turtle", "Dolphin", "Parrot"
    };

    // Rarity looks (see resource-pack/rarity_looks.py): client entity + variant per rarity.
    private static final String[] LOOK_ENTITY = {
            "wolf", "turtle", "wolf", "ocelot", "ocelot", "fox", "horse", "wolf", "wolf", "wolf"
    };
    private static final int[] LOOK_VARIANT = {1, 1, 2, 1, 2, 1, 1, 3, 4, 5};
    private static final String[] LOOK_TITLES = {
            "Волк", "Черепаха", "Лазурный волк", "Тигр", "Лев",
            "Кицунэ", "Древний олень", "Ангел", "Дракон Бездны", "Звёздный дракон"
    };
    /** First glyph index of every rarity word inside font/glyph_E2.png (U+E200 + index). */
    private static final int GLYPH_BASE = 0xE200;

    private Config data;
    private final Set<UUID> pendingHatches = new HashSet<>();
    private final Map<UUID, Map<Integer, Entity>> activeCompanions = new HashMap<>();
    private final Map<String, Long> companionAttackCooldowns = new HashMap<>();
    private final Map<String, Long> ultimateCooldowns = new HashMap<>();
    private final Map<UUID, EntityLiving> ownerFocusTargets = new HashMap<>();
    private final Map<UUID, Long> teamUltimateDecisionCooldowns = new HashMap<>();
    private static final long ULTIMATE_COOLDOWN_MS = 45_000L;
    private static final int HATCH_DELAY_TICKS = 100; // 5 seconds
    private int companionAuraTicks;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку NEXORA_Beasts.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        data = new Config(new File(getDataFolder(), "players.yml"), Config.YAML);
        registerCustomEntity("волк", NexoraWolfEntity.IDENTIFIER, NexoraWolfEntity::register);
        registerCustomEntity("лиса", NexoraFoxEntity.IDENTIFIER, NexoraFoxEntity::register);
        registerCustomEntity("кот", NexoraCatEntity.IDENTIFIER, NexoraCatEntity::register);
        registerCustomEntity("пчела", NexoraBeeEntity.IDENTIFIER, NexoraBeeEntity::register);
        registerCustomEntity("аксолотль", NexoraAxolotlEntity.IDENTIFIER, NexoraAxolotlEntity::register);
        registerCustomEntity("лошадь", NexoraHorseEntity.IDENTIFIER, NexoraHorseEntity::register);
        registerCustomEntity("панда", NexoraPandaEntity.IDENTIFIER, NexoraPandaEntity::register);
        registerCustomEntity("белый медведь", NexoraPolarBearEntity.IDENTIFIER, NexoraPolarBearEntity::register);
        registerCustomEntity("кролик", NexoraRabbitEntity.IDENTIFIER, NexoraRabbitEntity::register);
        registerCustomEntity("оцелот", NexoraOcelotEntity.IDENTIFIER, NexoraOcelotEntity::register);
        registerCustomEntity("черепаха", NexoraTurtleEntity.IDENTIFIER, NexoraTurtleEntity::register);
        registerCustomEntity("дельфин", NexoraDolphinEntity.IDENTIFIER, NexoraDolphinEntity::register);
        registerCustomEntity("попугай", NexoraParrotEntity.IDENTIFIER, NexoraParrotEntity::register);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleRepeatingTask(this, this::tickCompanions, 5);
        getLogger().info("NEXORA_Beasts включён. Коллекция, GUI и система питомцев готовы.");
    }

    private void registerCustomEntity(String displayName, String identifier, Runnable registration) {
        try {
            registration.run();
            getLogger().info("Зарегистрирована кастомная сущность " + identifier);
        } catch (RuntimeException ex) {
            getLogger().warning("Не удалось зарегистрировать " + displayName + " NEXORA; будет использован ванильный вид: " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        for (Map<Integer, Entity> companions : activeCompanions.values()) {
            for (Entity entity : companions.values()) {
                if (entity != null && !entity.isClosed()) entity.close();
            }
        }
        activeCompanions.clear();
        if (data != null) data.save();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline() && teamCount(player) > 0) summonTeam(player);
        }, 20);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID ownerId = event.getPlayer().getUniqueId();
        recallTeam(event.getPlayer(), false);
        companionAttackCooldowns.keySet().removeIf(key -> key.startsWith(ownerId + ":"));
        ultimateCooldowns.keySet().removeIf(key -> key.startsWith(ownerId + ":"));
        ownerFocusTargets.remove(ownerId);
        teamUltimateDecisionCooldowns.remove(ownerId);
    }

    /**
     * First PvE combat layer: when the owner lands a hit on a living mob,
     * summoned companions assist with a single, cooldown-limited strike.
     */
    @EventHandler
    public void onOwnerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player owner)) return;
        if (!(event.getEntity() instanceof EntityLiving target) || isActiveCompanion(target)) return;
        if (event.isCancelled()) return;

        Map<Integer, Entity> companions = activeCompanions.get(owner.getUniqueId());
        if (companions == null || companions.isEmpty()) return;
        // Remember the hostile mob the owner deliberately attacks so every pet
        // in the squad can coordinate on the same priority target.
        if (isHostileMob(target)) ownerFocusTargets.put(owner.getUniqueId(), target);

        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Entity> entry : companions.entrySet()) {
            int slot = entry.getKey();
            Entity pet = entry.getValue();
            if (!(pet instanceof EntityLiving) || pet.isClosed() || pet.getLevel() != target.getLevel()) continue;
            if (pet.distanceSquared(target) > 100) continue;

            String cooldownKey = owner.getUniqueId() + ":" + slot;
            long readyAt = companionAttackCooldowns.getOrDefault(cooldownKey, 0L);
            if (now < readyAt) continue;

            String beastId = data.getString(playerPath(owner) + ".team." + slot, "");
            if (beastId.isEmpty() || !hasBeast(owner, beastId)) continue;
            String base = beastPath(owner, beastId);
            double damage = Math.max(1.0, Math.min(30.0, stat(base, "power", 5) * 0.35));
            companionAttackCooldowns.put(cooldownKey, now + 1200L);
            gainBeastXp(owner, beastId, 6);

            target.attack(new EntityDamageByEntityEvent(
                    pet, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, (float) damage));
        }
    }

    /**
     * Protect every summoned companion from player damage and make the team defend its owner.
     */
    @EventHandler
    public void onCompanionProtectionAndDefense(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        // No player can directly damage a summoned NEXORA companion.
        if (isActiveCompanion(victim) && damager instanceof Player) {
            event.setCancelled();
            return;
        }

        // A companion must never attack its owner or another active companion.
        if (isActiveCompanion(damager) && (victim instanceof Player || isActiveCompanion(victim))) {
            UUID ownerId = getCompanionOwnerId(damager);
            if (ownerId != null && victim instanceof Player player
                    && ownerId.equals(player.getUniqueId())) {
                event.setCancelled();
            } else if (isActiveCompanion(victim)) {
                event.setCancelled();
            }
            return;
        }

        // Do not retaliate or dodge for hits cancelled by another plugin.
        if (event.isCancelled()) return;

        // Species passives: durable companions reduce incoming PvE damage.
        if (isActiveCompanion(victim) && damager instanceof EntityLiving attacker
                && attacker != victim && isHostileMob(attacker)) {
            String species = getCompanionSpecies(victim);
            int vitality = getCompanionStat(victim, "vitality", 20);
            int defense = getCompanionStat(victim, "defense", 2);
            // Vitality gives broad toughness; defense is the stronger dedicated mitigation stat.
            double reduction = Math.min(0.25, Math.max(0, vitality) * 0.0025)
                    + Math.min(0.45, Math.max(0, defense) * 0.004);
            reduction += switch (species) {
                case "turtle" -> 0.35;
                case "polarbear", "polar_bear" -> 0.25;
                case "panda" -> 0.15;
                default -> 0.0;
            };
            reduction = Math.min(0.70, reduction);
            event.setDamage((float) (event.getDamage() * (1.0 - reduction)));
            dodgeCompanion(victim, attacker);
        }

        // If someone (mob or player) attacks the owner, the owner's team retaliates.
        if (victim instanceof Player owner && !(damager instanceof EntityLiving living && isActiveCompanion(living))) {
            defendOwner(owner, damager);
        }
    }

    private void dodgeCompanion(Entity companion, EntityLiving attacker) {
        if (!(companion instanceof EntityLiving pet) || pet.isClosed()
                || attacker.isClosed() || pet.getLevel() != attacker.getLevel()) return;

        for (Map.Entry<UUID, Map<Integer, Entity>> ownerEntry : activeCompanions.entrySet()) {
            for (Map.Entry<Integer, Entity> petEntry : ownerEntry.getValue().entrySet()) {
                if (petEntry.getValue() != companion) continue;
                String cooldownKey = ownerEntry.getKey() + ":dodge:" + petEntry.getKey();
                long now = System.currentTimeMillis();
                if (now < companionAttackCooldowns.getOrDefault(cooldownKey, 0L)) return;

                String beastId = "";
                Player owner = null;
                for (Player online : getServer().getOnlinePlayers().values()) {
                    if (online.getUniqueId().equals(ownerEntry.getKey())) {
                        owner = online;
                        break;
                    }
                }
                if (owner != null) {
                    beastId = data.getString(playerPath(owner) + ".team." + petEntry.getKey(), "");
                }
                String species = owner != null && !beastId.isEmpty() && hasBeast(owner, beastId)
                        ? data.getString(beastPath(owner, beastId) + ".species", "wolf").toLowerCase(Locale.ROOT)
                        : "wolf";
                // Slow/heavy species use a short sidestep; agile species make a wider dodge.
                double step = switch (species) {
                    case "rabbit" -> 0.88;
                    case "fox", "ocelot", "parrot" -> 0.72;
                    case "wolf", "bee" -> 0.62;
                    case "turtle", "panda", "polar_bear", "polarbear" -> 0.22;
                    default -> 0.42;
                };
                double dx = pet.x - attacker.x;
                double dz = pet.z - attacker.z;
                double length = Math.sqrt(dx * dx + dz * dz);
                if (length < 0.001) {
                    dx = 1.0;
                    dz = 0.0;
                    length = 1.0;
                }
                pet.move(dx / length * step, 0.08, dz / length * step);
                pet.updateMovement();
                companionAttackCooldowns.put(cooldownKey, now + (species.equals("rabbit") || species.equals("fox") ? 650L : 900L));
                return;
            }
        }
    }

    private UUID getCompanionOwnerId(Entity companion) {
        for (Map.Entry<UUID, Map<Integer, Entity>> entry : activeCompanions.entrySet()) {
            if (entry.getValue().containsValue(companion)) return entry.getKey();
        }
        return null;
    }

    private void defendOwner(Player owner, Entity attacker) {
        if (!(attacker instanceof EntityLiving target) || target.isClosed()) return;
        Map<Integer, Entity> companions = activeCompanions.get(owner.getUniqueId());
        if (companions == null || companions.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Entity> entry : companions.entrySet()) {
            int slot = entry.getKey();
            Entity pet = entry.getValue();
            if (!(pet instanceof EntityLiving) || pet.isClosed() || pet == target
                    || pet.getLevel() != target.getLevel() || isActiveCompanion(target)) continue;
            if (pet.distanceSquared(target) > 144) continue;

            String cooldownKey = owner.getUniqueId() + ":defend:" + slot;
            if (now < companionAttackCooldowns.getOrDefault(cooldownKey, 0L)) continue;
            String beastId = data.getString(playerPath(owner) + ".team." + slot, "");
            if (beastId.isEmpty() || !hasBeast(owner, beastId)) continue;

            String base = beastPath(owner, beastId);
            double damage = Math.max(1.0, Math.min(30.0, stat(base, "power", 5) * 0.35));
            companionAttackCooldowns.put(cooldownKey, now + 1200L);
            target.attack(new EntityDamageByEntityEvent(
                    pet, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, (float) damage));
        }
    }

    private void tickCompanions() {
        boolean auraTick = (++companionAuraTicks % 4 == 0); // scheduler runs every 5 ticks
        for (Player player : getServer().getOnlinePlayers().values()) {
            Map<Integer, Entity> companions = activeCompanions.get(player.getUniqueId());
            if (companions == null || companions.isEmpty()) continue;
            for (Map.Entry<Integer, Entity> entry : companions.entrySet()) {
                Entity pet = entry.getValue();
                if (pet == null || pet.isClosed()) continue;
                if (auraTick) {
                    String beastId = data.getString(playerPath(player) + ".team." + entry.getKey(), "");
                    if (!beastId.isEmpty() && hasBeast(player, beastId)) {
                        spawnRarityAura(pet, data.getString(beastPath(player, beastId) + ".rarity", "common"));
                    }
                }

                calmCompanion(pet, player);

                String beastId = data.getString(playerPath(player) + ".team." + entry.getKey(), "");
                boolean fighting = false;
                if (!beastId.isEmpty() && hasBeast(player, beastId)
                        && pet instanceof EntityLiving livingPet) {
                    fighting = runCompanionAI(player, entry.getKey(), livingPet, beastId);
                }

                if (pet.getLevel() != player.getLevel()) {
                    pet.teleport(player.add(companionOffsetX(entry.getKey()), 0, companionOffsetZ(entry.getKey())));
                    continue;
                }

                // Do not pull a pet back into its owner while it is chasing or attacking.
                if (fighting) continue;

                cn.nukkit.math.Vector3 target = player.add(
                        companionOffsetX(entry.getKey()), 0, companionOffsetZ(entry.getKey()));
                double dx = target.x - pet.x;
                double dy = target.y - pet.y;
                double dz = target.z - pet.z;
                double distanceSquared = dx * dx + dy * dy + dz * dz;

                // Follow in 3D so companions do not get stranded when the owner
                // climbs, jumps down, or changes elevation.
                if (distanceSquared > 144 || Math.abs(dy) > 6.0) {
                    pet.teleport(target);
                } else if (distanceSquared > 4.0) {
                    double distance = Math.sqrt(distanceSquared);
                    double step = Math.min(0.62, distance - 2.0);
                    pet.move(dx / distance * step, dy / distance * step, dz / distance * step);
                    pet.updateMovement();
                }
            }
        }
    }


    /**
     * Autonomous combat brain: acquire nearby hostile mobs, close distance, attack,
     * and decide when the species ultimate is worth spending.
     */
    private boolean runCompanionAI(Player owner, int slot, EntityLiving pet, String beastId) {
        if (pet.isClosed() || pet.getLevel() != owner.getLevel()) return false;

        String base = beastPath(owner, beastId);
        String species = data.getString(base + ".species", "wolf").toLowerCase(Locale.ROOT);
        double searchRadius = species.equals("parrot") ? 14.0 : 10.0;
        EntityLiving target = null;
        double nearestDistance = searchRadius * searchRadius;
        int enemiesNearby = 0;
        for (Entity entity : pet.getLevel().getNearbyEntities(
                pet.getBoundingBox().grow(searchRadius, 6, searchRadius), pet)) {
            if (!(entity instanceof EntityLiving living) || living == pet || living.isClosed()
                    || living instanceof Player || isActiveCompanion(living)
                    || !isHostileMob(living)) continue;
            double distance = pet.distanceSquared(living);
            if (distance > searchRadius * searchRadius) continue;
            enemiesNearby++;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                target = living;
            }
        }

        // Squad focus: prefer the hostile mob the owner is currently fighting.
        EntityLiving focused = ownerFocusTargets.get(owner.getUniqueId());
        if (focused != null && (focused.isClosed() || focused.getLevel() != pet.getLevel()
                || !isHostileMob(focused))) {
            ownerFocusTargets.remove(owner.getUniqueId());
            focused = null;
        }
        if (focused != null && pet.distanceSquared(focused) <= 196.0) {
            target = focused;
            nearestDistance = pet.distanceSquared(focused);
        }
        if (target == null) return false;

        long now = System.currentTimeMillis();
        String attackKey = owner.getUniqueId() + ":ai:" + slot;
        long attackReady = companionAttackCooldowns.getOrDefault(attackKey, 0L);

        // Fast species reposition more aggressively; heavy species hold a little closer.
        double attackRange = switch (species) {
            case "bee", "dolphin", "parrot" -> 3.2;
            case "turtle", "panda", "polarbear", "polar_bear", "horse" -> 2.6;
            default -> 2.2;
        };
        if (nearestDistance > attackRange * attackRange) {
            double distance = Math.sqrt(nearestDistance);
            double speciesSpeed = switch (species) {
                case "fox", "rabbit", "dolphin" -> 1.35;
                case "horse" -> 1.45;
                case "parrot" -> 1.20;
                case "turtle", "panda", "polarbear", "polar_bear" -> 0.78;
                default -> 1.0;
            };
            double step = Math.min((species.equals("horse") || species.equals("wolf") ? 0.78 : 0.62)
                            * speciesSpeed, Math.max(0.12, distance - attackRange));
            pet.move((target.x - pet.x) / distance * step,
                    (target.y - pet.y) / distance * step,
                    (target.z - pet.z) / distance * step);
            pet.updateMovement();
        } else if (now >= attackReady) {
            double power = stat(base, "power", 5);
            double multiplier = switch (species) {
                case "wolf" -> 0.50;       // Pack Hunter
                case "bee" -> 0.46;        // Stinging Strike
                case "horse" -> 0.58;      // Charge
                case "polar_bear", "polarbear" -> 0.58; // Heavy Claw
                case "panda" -> 0.60;      // Heavy Paw
                case "ocelot", "cat" -> 0.46; // Quick Pounce
                case "fox" -> 0.40;
                case "rabbit" -> 0.30;
                case "parrot" -> 0.36;
                case "dolphin" -> 0.40;
                case "axolotl" -> 0.42;
                case "turtle" -> 0.40;
                default -> 0.35;
            };
            float damage = (float) Math.max(1.0, Math.min(24.0, power * multiplier));
            gainBeastXp(owner, beastId, 10);
            target.attack(new EntityDamageByEntityEvent(
                    pet, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
            triggerSpeciesCombatSkill(owner, slot, pet, target, species, power, now);
            long delay = switch (species) {
                case "cat", "ocelot", "fox", "rabbit" -> 820L;
                case "bee" -> 900L;
                case "panda", "polar_bear", "polarbear", "horse" -> 1350L;
                default -> 1100L;
            };
            companionAttackCooldowns.put(attackKey, now + delay);
        }

        // Count enemies clustered around the chosen target, not scattered across
        // the whole search radius; this prevents wasting an ultimate on a loose group.
        int clusteredEnemies = 0;
        for (Entity entity : target.getLevel().getNearbyEntities(
                target.getBoundingBox().grow(5, 4, 5), target)) {
            if (entity instanceof EntityLiving living && living != target && !living.isClosed()
                    && !(living instanceof Player) && !isActiveCompanion(living)
                    && isHostileMob(living) && target.distanceSquared(living) <= 25.0) {
                clusteredEnemies++;
            }
        }

        // The AI saves the ultimate for a real cluster or a dangerous close-range fight.
        String rarity = data.getString(base + ".rarity", "common").toLowerCase(Locale.ROOT);
        boolean highRarity = List.of("epic", "legendary", "mythic", "ancient",
                "divine", "forbidden", "absolute").contains(rarity);
        boolean groupFight = clusteredEnemies >= 2;
        boolean closeDanger = nearestDistance <= 2.25 && highRarity;
        String ultimateKey = owner.getUniqueId() + ":" + slot;
        long teamDecisionReady = teamUltimateDecisionCooldowns.getOrDefault(owner.getUniqueId(), 0L);
        if ((groupFight || closeDanger) && now >= teamDecisionReady
                && now >= ultimateCooldowns.getOrDefault(ultimateKey, 0L)) {
            teamUltimateDecisionCooldowns.put(owner.getUniqueId(), now + 5000L);
            useUltimate(owner, slot);
        }
        return true;
    }

    /**
     * Each species has a signature combat proc. The shared cooldown prevents
     * rapid repeated procs while keeping the passive meaningful in longer fights.
     */
    private void triggerSpeciesCombatSkill(Player owner, int slot, EntityLiving pet,
                                           EntityLiving target, String species, double power, long now) {
        String key = owner.getUniqueId() + ":skill:" + slot;
        if (now < companionAttackCooldowns.getOrDefault(key, 0L)
                || ThreadLocalRandom.current().nextDouble() > 0.22) return;

        double bonusMultiplier = switch (species) {
            case "wolf" -> 0.85;                 // Pack Rend
            case "fox" -> 0.70;                  // Ember Feint
            case "rabbit" -> 0.55;               // Moonstrike
            case "cat", "ocelot" -> 0.75;        // Shadow Claw
            case "bee" -> 0.80;                  // Piercing Sting
            case "axolotl" -> 0.72;              // Tidal Pulse
            case "horse" -> 1.00;                // Thunder Charge
            case "panda" -> 0.95;                // Jade Fist
            case "polarbear", "polar_bear" -> 1.05; // Frost Maul
            case "turtle" -> 0.65;               // Shell Burst
            case "dolphin" -> 0.70;              // Tidal Dash
            case "parrot" -> 0.62;               // Feather Volley
            default -> 0.60;
        };
        float bonusDamage = (float) Math.max(2.0, Math.min(32.0, power * bonusMultiplier));
        target.attack(new EntityDamageByEntityEvent(
                pet, target, EntityDamageEvent.DamageCause.MAGIC, bonusDamage));
        applySpeciesSkillMotion(pet, target, species);

        int[] rgb = switch (species) {
            case "wolf" -> new int[]{115, 205, 255};
            case "fox" -> new int[]{255, 125, 45};
            case "rabbit" -> new int[]{245, 175, 220};
            case "cat", "ocelot" -> new int[]{205, 145, 255};
            case "bee" -> new int[]{255, 220, 55};
            case "axolotl", "dolphin" -> new int[]{80, 200, 220};
            case "horse" -> new int[]{255, 190, 80};
            case "panda" -> new int[]{225, 235, 245};
            case "polarbear", "polar_bear" -> new int[]{175, 225, 255};
            case "turtle" -> new int[]{105, 205, 125};
            case "parrot" -> new int[]{235, 95, 155};
            default -> new int[]{190, 150, 255};
        };
        Vector3 effectPoint = new Vector3(target.x, target.y + 1.0, target.z);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2.0 * i / 6.0;
            Vector3 point = new Vector3(effectPoint.x + Math.cos(angle) * 0.45,
                    effectPoint.y + (i % 2) * 0.18, effectPoint.z + Math.sin(angle) * 0.45);
            target.getLevel().addParticle(new DustParticle(point, rgb[0], rgb[1], rgb[2]));
        }
        companionAttackCooldowns.put(key, now + 7000L);
    }

    /**
     * Species skills have distinct combat control, not only different names and damage.
     * Motion is deliberately short and bounded so mobs are displaced without being launched
     * across the map or into dangerous voids.
     */
    private void applySpeciesSkillMotion(EntityLiving pet, EntityLiving target, String species) {
        double dx = target.x - pet.x;
        double dz = target.z - pet.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) {
            dx = 1.0;
            dz = 0.0;
            length = 1.0;
        }
        double nx = dx / length;
        double nz = dz / length;
        double vx = 0.0;
        double vz = 0.0;
        double vy = 0.12;
        switch (species) {
            case "wolf" -> { // stagger the prey with a firm pack hit
                vx = nx * 0.42; vz = nz * 0.42; vy = 0.16;
            }
            case "fox" -> { // sidestep-like lateral feint
                vx = -nz * 0.34; vz = nx * 0.34; vy = 0.12;
            }
            case "rabbit" -> { // quick moon-jump
                vx = nx * 0.12; vz = nz * 0.12; vy = 0.48;
            }
            case "cat", "ocelot" -> { // short disruptive shadow swipe
                vx = nx * 0.24; vz = nz * 0.24; vy = 0.10;
            }
            case "bee" -> { // sting lifts the target briefly
                vx = nx * 0.08; vz = nz * 0.08; vy = 0.34;
            }
            case "axolotl" -> { // tidal pulse pushes away
                vx = nx * 0.32; vz = nz * 0.32; vy = 0.20;
            }
            case "horse" -> { // heavy forward charge
                vx = nx * 0.72; vz = nz * 0.72; vy = 0.24;
            }
            case "panda" -> { // powerful but controlled body-check
                vx = nx * 0.52; vz = nz * 0.52; vy = 0.14;
            }
            case "polarbear", "polar_bear" -> { // strongest impact
                vx = nx * 0.82; vz = nz * 0.82; vy = 0.28;
            }
            case "turtle" -> { // defensive shell burst
                vx = nx * 0.38; vz = nz * 0.38; vy = 0.30;
            }
            case "dolphin" -> { // fast water-like dash
                vx = nx * 0.58; vz = nz * 0.58; vy = 0.18;
            }
            case "parrot" -> { // feather gust
                vx = nx * 0.18; vz = nz * 0.18; vy = 0.26;
            }
            default -> { return; }
        }
        target.setMotion(new Vector3(vx, vy, vz));
    }

    private String getCompanionDataPath(Entity companion) {
        for (Map.Entry<UUID, Map<Integer, Entity>> ownerEntry : activeCompanions.entrySet()) {
            for (Map.Entry<Integer, Entity> petEntry : ownerEntry.getValue().entrySet()) {
                if (petEntry.getValue() != companion) continue;
                String ownerPath = "players." + ownerEntry.getKey();
                String id = data.getString(ownerPath + ".team." + petEntry.getKey(), "");
                if (id.isEmpty()) return "";
                String base = ownerPath + ".collection." + id;
                return data.exists(base + ".species") ? base : "";
            }
        }
        return "";
    }

    private String getCompanionSpecies(Entity companion) {
        String base = getCompanionDataPath(companion);
        return base.isEmpty() ? "" : data.getString(base + ".species", "").toLowerCase(Locale.ROOT);
    }

    private int getCompanionStat(Entity companion, String key, int fallback) {
        String base = getCompanionDataPath(companion);
        return base.isEmpty() ? fallback : stat(base, key, fallback);
    }

    private String speciesSkillName(String species) {
        return switch (species.toLowerCase(Locale.ROOT)) {
            case "wolf" -> "Разрыв стаи";
            case "fox" -> "Огненный финт";
            case "rabbit" -> "Лунный удар";
            case "cat", "ocelot" -> "Теневой коготь";
            case "bee" -> "Пронзающее жало";
            case "axolotl" -> "Приливный импульс";
            case "horse" -> "Громовой натиск";
            case "panda" -> "Нефритовый кулак";
            case "polarbear", "polar_bear" -> "Ледяная сокрушительная атака";
            case "turtle" -> "Взрыв панциря";
            case "dolphin" -> "Морской рывок";
            case "parrot" -> "Шквал перьев";
            default -> "Особый удар";
        };
    }

    private String speciesSkillDescription(String species) {
        return switch (species.toLowerCase(Locale.ROOT)) {
            case "wolf" -> "§7Сильный удар стаи отталкивает цель. Шанс 22%, перезарядка 7 сек.";
            case "fox" -> "§7Боковой финт сбивает цель с позиции. Шанс 22%, перезарядка 7 сек.";
            case "rabbit" -> "§7Лунный прыжок подбрасывает врага вверх. Шанс 22%, перезарядка 7 сек.";
            case "cat", "ocelot" -> "§7Теневой коготь слегка отбрасывает врага. Шанс 22%, перезарядка 7 сек.";
            case "bee" -> "§7Жало приподнимает цель коротким импульсом. Шанс 22%, перезарядка 7 сек.";
            case "axolotl" -> "§7Приливный импульс отталкивает противника. Шанс 22%, перезарядка 7 сек.";
            case "horse" -> "§7Громовой натиск мощно отбрасывает цель. Шанс 22%, перезарядка 7 сек.";
            case "panda" -> "§7Нефритовый кулак сбивает врага с ног. Шанс 22%, перезарядка 7 сек.";
            case "polarbear", "polar_bear" -> "§7Ледяной удар наносит самый сильный толчок. Шанс 22%, перезарядка 7 сек.";
            case "turtle" -> "§7Взрыв панциря подбрасывает и отталкивает врага. Шанс 22%, перезарядка 7 сек.";
            case "dolphin" -> "§7Морской рывок резко отталкивает цель. Шанс 22%, перезарядка 7 сек.";
            case "parrot" -> "§7Шквал перьев приподнимает противника. Шанс 22%, перезарядка 7 сек.";
            default -> "§7Особый удар отталкивает цель. Шанс 22%, перезарядка 7 сек.";
        };
    }

    private String speciesPassiveName(String species) {
        return switch (species.toLowerCase(Locale.ROOT)) {
            case "wolf" -> "§6Охотник стаи";
            case "fox" -> "§aЛовкий следопыт";
            case "rabbit" -> "§aПрыжок уворота";
            case "cat", "ocelot" -> "§eБыстрые когти";
            case "bee" -> "§6Ядовитое жало";
            case "axolotl" -> "§bВодный боец";
            case "horse" -> "§6Разгон";
            case "panda" -> "§fТяжёлая лапа";
            case "polarbear", "polar_bear" -> "§bМедвежья мощь";
            case "turtle" -> "§2Крепкий панцирь";
            case "dolphin" -> "§bМорской рывок";
            case "parrot" -> "§dДозор";
            default -> "§7Инстинкт зверя";
        };
    }

    private String speciesPassiveDescription(String species) {
        return switch (species.toLowerCase(Locale.ROOT)) {
            case "wolf" -> "§7Увеличенный урон в бою рядом с хозяином.";
            case "fox" -> "§7Быстрее сближается с целью и чаще уклоняется.";
            case "rabbit" -> "§7Самый резкий уворот и быстрое восстановление манёвра.";
            case "cat", "ocelot" -> "§7Быстрые атаки и повышенный урон когтями.";
            case "bee" -> "§7Усиленная атака жалом.";
            case "axolotl" -> "§7Сбалансированный боец с усиленной атакой.";
            case "horse" -> "§7Быстрый разгон и мощный удар с наскока.";
            case "panda" -> "§7Мощный удар, но медленнее атакует; получает меньше урона.";
            case "polarbear", "polar_bear" -> "§7Тяжёлый удар и сниженный входящий урон.";
            case "turtle" -> "§7Панцирь снижает входящий урон на 35%.";
            case "dolphin" -> "§7Быстро перемещается к противнику.";
            case "parrot" -> "§7Замечает врагов на большем расстоянии.";
            default -> "§7Особый природный инстинкт.";
        };
    }

    private boolean isHostileMob(EntityLiving entity) {
        if (entity == null || entity.isClosed() || entity instanceof Player) return false;
        String className = entity.getClass().getName().toLowerCase(Locale.ROOT);
        String simpleName = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        // Nukkit-MOT versions place hostile mobs in different packages (often .mob.),
        // so package-only checks miss zombies and several other standard enemies.
        if (className.contains(".monster.") || className.contains(".hostile.")) return true;
        String[] hostileTypes = {
                "zombie", "husk", "drowned", "skeleton", "stray", "wither_skeleton",
                "creeper", "spider", "cave_spider", "enderman", "witch", "slime",
                "magma_cube", "blaze", "ghast", "guardian", "elder_guardian",
                "phantom", "silverfish", "endermite", "pillager", "vindicator",
                "evoker", "ravager", "vex", "warden", "wither", "ender_dragon",
                "zoglin", "hoglin", "piglin_brute"
        };
        for (String type : hostileTypes) {
            if (simpleName.contains(type)) return true;
        }
        return false;
    }

    /**
     * Lightweight rarity aura: four colored dust motes per active pet once per second.
     * This is visual only and does not affect combat or entity targeting.
     */
    private void spawnRarityAura(Entity pet, String rarity) {
        int[] rgb = switch (rarity.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> new int[]{75, 220, 105};
            case "rare" -> new int[]{65, 210, 245};
            case "epic" -> new int[]{175, 85, 235};
            case "legendary" -> new int[]{255, 190, 55};
            case "mythic" -> new int[]{245, 80, 190};
            case "ancient" -> new int[]{45, 190, 175};
            case "divine" -> new int[]{255, 235, 145};
            case "forbidden" -> new int[]{235, 55, 65};
            case "absolute" -> new int[]{225, 245, 255};
            default -> new int[]{155, 155, 165};
        };
        double y = pet.y + Math.max(0.35, pet.getHeight() * 0.55);
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI * 2.0 * i) / 4.0;
            Vector3 point = new Vector3(
                    pet.x + Math.cos(angle) * 0.42,
                    y + (i % 2 == 0 ? 0.12 : -0.08),
                    pet.z + Math.sin(angle) * 0.42);
            pet.getLevel().addParticle(new DustParticle(point, rgb[0], rgb[1], rgb[2]));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (sender instanceof Player player) openMainMenu(player);
            else sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "gui" -> {
                if (requirePlayer(sender)) openMainMenu((Player) sender);
            }
            case "collection", "list" -> {
                if (requirePlayer(sender)) openCollectionMenu((Player) sender);
            }
            case "summon", "call" -> {
                if (requirePlayer(sender)) summonTeam((Player) sender);
            }
            case "recall", "hide" -> {
                if (requirePlayer(sender)) recallTeam((Player) sender, true);
            }
            case "ultimate", "ult" -> {
                if (!requirePlayer(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts ultimate <слот 1-3>");
                else {
                    try { useUltimate((Player) sender, Integer.parseInt(args[1])); }
                    catch (NumberFormatException ex) { sender.sendMessage("§cУкажи слот от 1 до 3."); }
                }
            }
            case "team" -> {
                if (requirePlayer(sender)) {
                    if (args.length == 1) openTeamMenu((Player) sender);
                    else if (args.length == 3) setTeamSlot((Player) sender, args[1], args[2]);
                    else sender.sendMessage("§cИспользование: /beasts team <слот 1-3> <ID существа>");
                }
            }
            case "train", "upgrade", "develop" -> {
                if (!requirePlayer(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts upgrade <ID>");
                else openBeastUpgradeMenu((Player) sender, args[1]);
            }
            case "info" -> {
                if (!requirePlayer(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts info <ID>");
                else showBeastInfo((Player) sender, args[1]);
            }
            case "release" -> {
                if (!requirePlayer(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts release <ID>");
                else releaseBeast((Player) sender, args[1]);
            }
            case "giveorb" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts giveorb <игрок> [количество]");
                else giveOrb(sender, args);
            }
            case "eggs" -> showEggCatalog(sender);
            case "giveeggs" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts giveeggs <игрок>");
                else giveEggShowcase(sender, args[1]);
            }
            case "giveegg" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length < 4) sender.sendMessage("§cИспользование: /beasts giveegg <игрок> <вид|random> <редкость>");
                else giveEgg(sender, args);
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getEntity();
        if (!(target instanceof EntityLiving) || target instanceof Player) return;

        Item hand = player.getInventory().getItemInHand();
        if (hand == null || !ORB_NAME.equals(hand.getCustomName())) return;

        event.setCancelled();
        String species = normalizeSpecies(target.getName());
        String rarity = rollWildRarity();
        String id = addBeast(player, species, rarity, "wild");
        consumeOne(player, hand);
        target.close();

        player.sendMessage("§aТы приручил существо!");
        player.sendMessage("§f" + prettySpecies(species) + " §7[" + rarityColor(rarity) + rarityName(rarity) + "§7]");
        player.sendMessage("§7ID существа: §f" + id + " §8| §7Коллекция: §f" + collectionSize(player));
    }

    @EventHandler
    public void onEggUse(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
                && event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Item hand = player.getInventory().getItemInHand();
        if (hand == null || hand.getCustomName() == null
                || !(EGG_NAME.equals(hand.getCustomName()) || hand.getCustomName().startsWith(EGG_PREFIX))) return;

        String species = "random";
        String rarity = "common";
        String[] lore = hand.getLore();
        if (lore != null) {
            for (String line : lore) {
                String clean = stripColors(line).trim();
                if (clean.startsWith("NEXORA_SPECIES:")) species = clean.substring("NEXORA_SPECIES:".length()).trim();
                if (clean.startsWith("NEXORA_RARITY:")) rarity = clean.substring("NEXORA_RARITY:".length()).trim().toLowerCase(Locale.ROOT);
            }
        }
        if (!isRarity(rarity)) rarity = "common";
        if (species.equalsIgnoreCase("random")) {
            species = rollEggSpecies(rarity, player);
        }

        event.setCancelled();
        UUID playerId = player.getUniqueId();
        if (pendingHatches.contains(playerId)) {
            player.sendMessage("§eУ тебя уже вылупляется яйцо. Подожди немного.");
            return;
        }

        pendingHatches.add(playerId);
        final String hatchSpecies = species;
        final String hatchRarity = rarity;
        consumeOne(player, hand);
        player.sendMessage("§6§lВылупление началось!");
        player.sendMessage("§7Яйцо заряжается магией... §eПодожди 5 секунд.");
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            pendingHatches.remove(playerId);
            String id = addBeast(player, normalizeSpecies(hatchSpecies), hatchRarity, "egg");
            if (player.isOnline()) {
                player.sendMessage("§a§lЯЙЦО ВЫЛУПИЛОСЬ!");
                player.sendMessage("§f" + prettySpecies(hatchSpecies) + " §7[" + rarityColor(hatchRarity) + rarityName(hatchRarity) + "§7]");
                player.sendMessage("§7Новое существо добавлено в коллекцию. ID: §f" + id);
                player.sendMessage("§7Характеристики можно посмотреть: §f/beasts info " + id);
            }
        }, HATCH_DELAY_TICKS);
    }

    // -------------------- NEXORA touch-friendly GUI --------------------

    private ElementButton iconButton(String text, String texturePath) {
        return new ElementButton(text, new ElementButtonImageData(
                ElementButtonImageData.IMAGE_DATA_TYPE_PATH, texturePath));
    }

    private void openMainMenu(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§0NEXORA: BEASTS", 
                "§7Твоя связь с дикими существами.\n\n"
                + "§fКоллекция: §b" + collectionSize(player) + " §8| §fКоманда: §b" + teamCount(player) + "/3\n"
                + "§7Выбери раздел ниже.");
        form.addButton(iconButton("§l§b✦  МОЯ КОЛЛЕКЦИЯ\n§8Все пойманные существа", "textures/ui/collection"));
        form.addButton(iconButton("§l§6⚔  МОЯ КОМАНДА\n§8Три активных существа", "textures/ui/team"));
        form.addButton(iconButton("§l§d✧  КАТАЛОГ ЯИЦ\n§8Десять уровней редкости", "textures/ui/eggs"));
        form.addButton(new ElementButton("§l§7✕  ЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            switch (form.getResponse().getClickedButtonId()) {
                case 0 -> openCollectionMenu(player);
                case 1 -> openTeamMenu(player);
                case 2 -> openEggCatalogMenu(player);
                default -> { }
            }
        }));
        player.showFormWindow(form);
    }

    private void openCollectionMenu(Player player) {
        Map<String, Object> entries = collection(player);
        FormWindowSimple form = new FormWindowSimple("§l§0МОЯ КОЛЛЕКЦИЯ",
                "§7Собрано существ: §b" + entries.size() + "\n§8Нажми на существо, чтобы открыть его карточку.");
        List<String> ids = new ArrayList<>(entries.keySet());
        for (String id : ids) {
            String base = beastPath(player, id);
            String species = data.getString(base + ".species", "unknown");
            String rarity = data.getString(base + ".rarity", "common");
            int level = data.getInt(base + ".level", 1);
            form.addButton(new ElementButton(rarityColor(rarity) + "✦ " + prettySpecies(species)
                    + "\n§8" + rarityName(rarity) + " · Ур. " + level + " · " + id));
        }
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < ids.size()) openBeastCard(player, ids.get(selected));
            else openMainMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void openBeastCard(Player player, String id) {
        if (!hasBeast(player, id)) { openCollectionMenu(player); return; }
        String base = beastPath(player, id);
        String species = data.getString(base + ".species", "unknown");
        String rarity = data.getString(base + ".rarity", "common");
        int level = data.getInt(base + ".level", 1);
        String details = "§7" + prettySpecies(species) + "  " + rarityColor(rarity) + "◆ " + rarityName(rarity)
                + "\n§6Эволюция: §f" + evolutionRank(level)
                + "\n§dПассивный навык: §f" + speciesPassiveName(species)
                + "\n§8" + speciesPassiveDescription(species)
                + "\n§dАктивный приём: §f" + speciesSkillName(species)
                + "\n§8" + speciesSkillDescription(species)
                + "\n\n§fУровень: §a" + level + "\n§fБоевой опыт: §a" + data.getInt(base + ".xp", 0)
                + "§7 / " + xpToNext(level)
                + "\n§fОчки развития: §e" + data.getInt(base + ".upgradePoints", 0)
                + "\n§dУровень ульты: §f" + data.getInt(base + ".ultimateLevel", 0) + "/5"
                + "\n\n§c❤ §fЖивучесть: §c" + stat(base, "vitality", 20)
                + "\n§6⚔ §fСила: §6" + stat(base, "power", 5)
                + "\n§b⬟ §fЗащита: §b" + stat(base, "defense", 2)
                + "\n\n§8Опыт питомец получает, участвуя в боях. Очки уровня трать на характеристики."
                + "\n§8ID: " + id;
        FormWindowSimple form = new FormWindowSimple("§l§0КАРТОЧКА СУЩЕСТВА", details);
        form.addButton(iconButton("§l§a✦ РАЗВИТИЕ\n§8Распределить очки характеристик", "textures/ui/training"));
        form.addButton(new ElementButton("§l§d✹ УЛУЧШИТЬ УЛЬТУ\n§8Повысить силу Пробуждения"));
        form.addButton(new ElementButton("§l§6⚔ В КОМАНДУ\n§8Назначить в один из 3 слотов"));
        form.addButton(iconButton("§l§c✕ ОТПУСТИТЬ\n§8Удалить из коллекции", "textures/ui/release"));
        form.addButton(new ElementButton("§7← К КОЛЛЕКЦИИ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            switch (form.getResponse().getClickedButtonId()) {
                case 0 -> openBeastUpgradeMenu(player, id);
                case 1 -> openUltimateUpgradeMenu(player, id);
                case 2 -> openAssignSlotMenu(player, id);
                case 3 -> confirmRelease(player, id);
                default -> openCollectionMenu(player);
            }
        }));
        player.showFormWindow(form);
    }

    private void openAssignSlotMenu(Player player, String id) {
        FormWindowCustom form = new FormWindowCustom("§l§0НАЗНАЧИТЬ В КОМАНДУ");
        form.addElement(new ElementDropdown("Выбери слот активной команды", List.of("Слот 1", "Слот 2", "Слот 3")));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int slot = form.getResponse().getDropdownResponse(0).getElementID() + 1;
            setTeamSlot(player, String.valueOf(slot), id);
            openTeamMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void openTeamMenu(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§0АКТИВНАЯ КОМАНДА",
                "§7Выбери слот, чтобы назначить в него существо из коллекции.\n§8Максимум три существа.");
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            if (id.isEmpty() || !hasBeast(player, id)) {
                form.addButton(new ElementButton("§e＋ СЛОТ " + slot + " · ПУСТО\n§8Нажми, чтобы выбрать существо"));
            } else {
                String base = beastPath(player, id);
                String species = data.getString(base + ".species", "unknown");
                String rarity = data.getString(base + ".rarity", "common");
                form.addButton(new ElementButton(rarityColor(rarity) + "✦ СЛОТ " + slot + " · " + prettySpecies(species)
                        + "\n§8" + rarityName(rarity) + " · Ур. " + data.getInt(base + ".level", 1)));
            }
        }
        form.addButton(new ElementButton("§a✦ ПРИЗВАТЬ КОМАНДУ"));
        form.addButton(new ElementButton("§c✕ ОТОЗВАТЬ ПИТОМЦЕВ"));
        form.addButton(new ElementButton("§d✹ УЛЬТА ПИТОМЦА\n§8Мощная атака по области · перезарядка 45 сек"));
        form.addButton(new ElementButton("§7← ГЛАВНОЕ МЕНЮ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < 3) openSelectBeastForSlot(player, selected + 1);
            else if (selected == 3) summonTeam(player);
            else if (selected == 4) { recallTeam(player, true); openTeamMenu(player); }
            else if (selected == 5) openUltimateSlotMenu(player);
            else openMainMenu(player);
        }));
        player.showFormWindow(form);
    }


    private void openUltimateSlotMenu(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§5ПРОБУЖДЕНИЕ",
                "§7Выбери питомца, который выпустит свою ультимативную силу.\n§8Нужен призванный питомец в активной команде.");
        List<Integer> slots = new ArrayList<>();
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            Map<Integer, Entity> active = activeCompanions.get(player.getUniqueId());
            Entity pet = active == null ? null : active.get(slot);
            if (!id.isEmpty() && hasBeast(player, id) && pet != null && !pet.isClosed()) {
                String base = beastPath(player, id);
                String species = data.getString(base + ".species", "unknown").toLowerCase(Locale.ROOT);
                form.addButton(new ElementButton("§d✹ СЛОТ " + slot + " · " + prettySpecies(species) +
                        "\n§8«" + ultimateName(species) + "» · Сила: " +
                        Math.min(70, stat(base, "power", 5) * 2)));
                slots.add(slot);
            }
        }
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < slots.size()) useUltimate(player, slots.get(selected));
            else openTeamMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void useUltimate(Player player, int slot) {
        if (slot < 1 || slot > 3) {
            player.sendMessage("§cСлот должен быть от 1 до 3.");
            return;
        }
        String id = data.getString(playerPath(player) + ".team." + slot, "");
        Map<Integer, Entity> active = activeCompanions.get(player.getUniqueId());
        Entity pet = active == null ? null : active.get(slot);
        if (id.isEmpty() || !hasBeast(player, id) || !(pet instanceof EntityLiving) || pet.isClosed()) {
            player.sendMessage("§cСначала назначь и призови питомца в этот слот.");
            return;
        }

        String cooldownKey = player.getUniqueId() + ":" + slot;
        long now = System.currentTimeMillis();
        long readyAt = ultimateCooldowns.getOrDefault(cooldownKey, 0L);
        if (now < readyAt) {
            long seconds = (readyAt - now + 999L) / 1000L;
            player.sendMessage("§d✹ Ульта перезаряжается: §f" + seconds + " сек.");
            return;
        }

        String base = beastPath(player, id);
        String species = data.getString(base + ".species", "wolf").toLowerCase(Locale.ROOT);
        int power = stat(base, "power", 5);
        int ultimateLevel = Math.max(0, Math.min(5, data.getInt(base + ".ultimateLevel", 0)));
        double damage = Math.max(8.0, Math.min(100.0, power * 2.0))
                * (1.0 + ultimateLevel * 0.15);
        double ultimateRadius = 5.0 + ultimateLevel * 0.5;
        double centerX = pet.x;
        double centerY = pet.y + 1.0;
        double centerZ = pet.z;
        EntityLiving nearest = null;
        double nearestDistance = 12.0 * 12.0;

        for (Entity entity : pet.getLevel().getNearbyEntities(pet.getBoundingBox().grow(12, 12, 12), pet)) {
            if (!(entity instanceof EntityLiving living) || entity instanceof Player || entity == pet || entity.isClosed()) continue;
            if (isActiveCompanion(entity) || !isHostileMob(living)) continue;
            double distance = pet.distanceSquared(entity);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = living;
            }
        }
        if (nearest != null) {
            centerX = nearest.x;
            centerY = nearest.y + 1.0;
            centerZ = nearest.z;
        }

        Vector3 center = new Vector3(centerX, centerY, centerZ);
        var level = pet.getLevel();
        level.addSound(center, Sound.BLOCK_BELL_HIT, 1.4f, 0.65f);
        level.addParticleEffect(center, ParticleEffect.ENDROD);
        level.addParticleEffect(center, ParticleEffect.HUGE_EXPLOSION_LEVEL);
        spawnUltimateVisual(level, centerX, centerY, centerZ, species);

        int hitCount = 0;
        // Search only nearby entities instead of iterating every loaded chunk on the server.
        for (Entity entity : level.getNearbyEntities(pet.getBoundingBox().grow(17, 17, 17), pet)) {
            if (!(entity instanceof EntityLiving target) || entity instanceof Player || entity == pet || entity.isClosed()) continue;
            if (isActiveCompanion(entity) || !isHostileMob(target)) continue;
            double dx = entity.x - centerX;
            double dy = entity.y - centerY;
            double dz = entity.z - centerZ;
            if (dx * dx + dy * dy + dz * dz > ultimateRadius * ultimateRadius) continue;
            if (target.attack(new EntityDamageByEntityEvent(pet, target, EntityDamageEvent.DamageCause.MAGIC, (float) damage))) {
                hitCount++;
            }
        }

        long cooldown = Math.max(35_000L, ULTIMATE_COOLDOWN_MS - ultimateLevel * 2_000L);
        ultimateCooldowns.put(cooldownKey, now + cooldown);
        player.sendMessage("§d§l✹ ПРОБУЖДЕНИЕ! §r§f" + prettySpecies(species)
                + " §dиспользовал «" + ultimateName(species) + "»! §7Ур. " + ultimateLevel
                + " §8· §7Урон: §c" + (int) damage + " §8· §7Радиус: §f" + String.format(Locale.US, "%.1f", ultimateRadius)
                + " §8· §7Целей: §f" + hitCount);
    }

    /**
     * Species-specific ultimate visuals. Kept cosmetic so every effect remains safe
     * for the combat rules and does not change damage or targeting.
     */
    private void spawnUltimateVisual(cn.nukkit.level.Level level, double x, double y, double z, String species) {
        int[] rgb = switch (species) {
            case "wolf" -> new int[]{115, 205, 255};
            case "fox" -> new int[]{255, 125, 45};
            case "cat" -> new int[]{205, 145, 255};
            case "bee" -> new int[]{255, 220, 55};
            case "axolotl" -> new int[]{95, 225, 205};
            case "horse" -> new int[]{255, 190, 80};
            case "panda" -> new int[]{225, 235, 245};
            case "polarbear", "polar_bear" -> new int[]{175, 225, 255};
            case "rabbit" -> new int[]{245, 175, 220};
            case "ocelot" -> new int[]{245, 205, 105};
            case "turtle" -> new int[]{105, 205, 125};
            case "dolphin" -> new int[]{80, 180, 255};
            case "parrot" -> new int[]{235, 95, 155};
            default -> new int[]{190, 150, 255};
        };
        List<Vector3> points = new ArrayList<>();
        switch (species) {
            case "wolf" -> { // three claw-shaped arcs
                for (int claw = -1; claw <= 1; claw++) for (int i = 0; i < 10; i++) {
                    double a = -0.52 + i * 0.105;
                    double r = 1.0 + i * 0.16;
                    points.add(new Vector3(x + Math.sin(a) * r + claw * 0.58, y + 0.12, z + Math.cos(a) * r));
                }
            }
            case "fox" -> { // rising flame spiral
                for (int i = 0; i < 42; i++) {
                    double a = i * 0.31;
                    double r = 0.35 + i * 0.055;
                    points.add(new Vector3(x + Math.cos(a) * r, y + 0.08 + i * 0.055, z + Math.sin(a) * r));
                }
            }
            case "cat" -> { // five-point star
                for (int arm = 0; arm < 5; arm++) for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * arm / 5.0;
                    double r = (i % 2 == 0 ? 2.9 : 1.35) * (i / 5.0 + 0.55);
                    points.add(new Vector3(x + Math.cos(a) * r, y + 0.25 + (i % 2) * 0.25, z + Math.sin(a) * r));
                }
            }
            case "bee" -> { // two hovering rings
                for (int ring = 0; ring < 2; ring++) for (int i = 0; i < 20; i++) {
                    double a = Math.PI * 2 * i / 20.0;
                    double r = ring == 0 ? 1.65 : 2.65;
                    points.add(new Vector3(x + Math.cos(a) * r, y + 0.25 + ring * 0.8, z + Math.sin(a) * r));
                }
            }
            case "axolotl" -> { // bubbles rising around the center
                for (int column = 0; column < 8; column++) for (int i = 0; i < 4; i++) {
                    double a = Math.PI * 2 * column / 8.0;
                    double r = 1.5 + (i % 2) * 0.35;
                    points.add(new Vector3(x + Math.cos(a) * r, y + i * 0.55, z + Math.sin(a) * r));
                }
            }
            case "horse" -> { // expanding shockwave rings
                for (int ring = 0; ring < 3; ring++) for (int i = 0; i < 18; i++) {
                    double a = Math.PI * 2 * i / 18.0;
                    double r = 1.0 + ring * 0.95;
                    points.add(new Vector3(x + Math.cos(a) * r, y + 0.08 + ring * 0.16, z + Math.sin(a) * r));
                }
            }
            case "panda" -> { // twin balanced spirals
                for (int side = 0; side < 2; side++) for (int i = 0; i < 18; i++) {
                    double a = i * 0.38 + side * Math.PI;
                    double r = 0.35 + i * 0.105;
                    points.add(new Vector3(x + Math.cos(a) * r, y + 0.2 + side * 0.35, z + Math.sin(a) * r));
                }
            }
            case "polarbear", "polar_bear" -> { // six-armed snowflake
                for (int arm = 0; arm < 6; arm++) for (int i = 1; i <= 5; i++) {
                    double a = Math.PI * 2 * arm / 6.0;
                    double r = i * 0.52;
                    points.add(new Vector3(x + Math.cos(a) * r, y + 0.15, z + Math.sin(a) * r));
                    if (i >= 3) {
                        double branch = r - 0.65;
                        points.add(new Vector3(x + Math.cos(a + 0.42) * branch, y + 0.15, z + Math.sin(a + 0.42) * branch));
                        points.add(new Vector3(x + Math.cos(a - 0.42) * branch, y + 0.15, z + Math.sin(a - 0.42) * branch));
                    }
                }
            }
            case "rabbit" -> { // quick ascending moon spiral
                for (int i = 0; i < 36; i++) {
                    double a = i * 0.48;
                    double r = 0.55 + (i % 9) * 0.19;
                    points.add(new Vector3(x + Math.cos(a) * r, y + i * 0.065, z + Math.sin(a) * r));
                }
            }
            case "ocelot" -> { // sweeping crescent
                for (int i = 0; i <= 28; i++) {
                    double a = -Math.PI * 0.72 + i * (Math.PI * 1.44 / 28.0);
                    points.add(new Vector3(x + Math.cos(a) * 2.8, y + 0.15 + Math.sin(a) * 0.45, z + Math.sin(a) * 2.8));
                }
            }
            case "turtle" -> { // hexagonal shell outline
                for (int edge = 0; edge < 6; edge++) {
                    double a1 = Math.PI * 2 * edge / 6.0;
                    double a2 = Math.PI * 2 * (edge + 1) / 6.0;
                    for (int i = 0; i <= 5; i++) {
                        double t = i / 5.0;
                        points.add(new Vector3(x + (Math.cos(a1) * (1 - t) + Math.cos(a2) * t) * 2.4,
                                y + 0.18, z + (Math.sin(a1) * (1 - t) + Math.sin(a2) * t) * 2.4));
                    }
                }
            }
            case "dolphin" -> { // two flowing wave lines
                for (int wave = 0; wave < 2; wave++) for (int i = 0; i < 24; i++) {
                    double t = i / 23.0;
                    points.add(new Vector3(x - 2.8 + t * 5.6, y + 0.25 + Math.sin(t * Math.PI * 3 + wave * Math.PI) * 0.48,
                            z + (wave == 0 ? -0.65 : 0.65)));
                }
            }
            case "parrot" -> { // colorful feather fan
                for (int feather = 0; feather < 9; feather++) for (int i = 1; i <= 5; i++) {
                    double a = -Math.PI * 0.85 + feather * (Math.PI * 1.7 / 8.0);
                    double r = i * 0.52;
                    points.add(new Vector3(x + Math.cos(a) * r, y + 0.12 + i * 0.12, z + Math.sin(a) * r));
                }
            }
            default -> {
                for (int i = 0; i < 32; i++) {
                    double a = Math.PI * 2 * i / 32.0;
                    points.add(new Vector3(x + Math.cos(a) * 2.5, y + 0.2, z + Math.sin(a) * 2.5));
                }
            }
        }
        for (Vector3 point : points) level.addParticle(new DustParticle(point, rgb[0], rgb[1], rgb[2]));
    }

    private String ultimateName(String species) {
        return switch (species) {
            case "wolf" -> "Клык бури";
            case "fox" -> "Лисье пламя";
            case "cat" -> "Звёздный прыжок";
            case "bee" -> "Рой света";
            case "axolotl" -> "Прилив жизни";
            case "horse" -> "Громовой галоп";
            case "panda" -> "Нефритовый удар";
            case "polarbear", "polar_bear" -> "Ледяной шторм";
            case "rabbit" -> "Лунный скачок";
            case "ocelot" -> "Теневой рывок";
            case "turtle" -> "Панцирный импульс";
            case "dolphin" -> "Волна глубин";
            case "parrot" -> "Пернатый шквал";
            default -> "Пробуждение";
        };
    }

    private boolean isActiveCompanion(Entity entity) {
        for (Map<Integer, Entity> team : activeCompanions.values()) {
            if (team.containsValue(entity)) return true;
        }
        return false;
    }

    private void openSelectBeastForSlot(Player player, int slot) {
        Map<String, Object> entries = collection(player);
        FormWindowSimple form = new FormWindowSimple("§l§0ВЫБОР СУЩЕСТВА",
                "§7Назначение в слот " + slot + ".\n§8Выбери существо из своей коллекции.");
        List<String> ids = new ArrayList<>(entries.keySet());
        for (String id : ids) {
            String base = beastPath(player, id);
            String rarity = data.getString(base + ".rarity", "common");
            form.addButton(new ElementButton(rarityColor(rarity) + prettySpecies(data.getString(base + ".species", "unknown"))
                    + "\n§8" + rarityName(rarity) + " · " + id));
        }
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < ids.size()) setTeamSlot(player, String.valueOf(slot), ids.get(selected));
            openTeamMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void confirmRelease(Player player, String id) {
        FormWindowModal form = new FormWindowModal("§l§0ОТПУСТИТЬ СУЩЕСТВО",
                "§cТы уверен, что хочешь навсегда удалить это существо из коллекции?\n§8Это действие нельзя отменить.",
                "§cОТПУСТИТЬ", "§7ОТМЕНА");
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            if (form.getResponse().getClickedButtonId() == 0) releaseBeast(player, id);
            openCollectionMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void openEggCatalogMenu(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§0АРХИВ ЯИЦ NEXORA",
                "§7Десять ступеней редкости. Цвет яйца и существа зависит от его ранга.\n§8Яйца можно получить у администрации или найти в игровых событиях.");
        for (int i = 0; i < RARITIES.length; i++) {
            form.addButton(new ElementButton(RARITY_COLORS[i] + "◆ " + RARITY_NAMES[i]
                    + "\n§8" + RARITIES[i].toUpperCase(Locale.ROOT)));
        }
        form.addButton(new ElementButton("§7← ГЛАВНОЕ МЕНЮ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < RARITIES.length) {
                player.sendMessage(RARITY_COLORS[selected] + "◆ " + RARITY_NAMES[selected]
                        + " §8— §7редкость яйца NEXORA.");
                openEggCatalogMenu(player);
            } else openMainMenu(player);
        }));
        player.showFormWindow(form);
    }

    // -------------------- Summoned companions --------------------

    private double companionOffsetX(int slot) {
        return slot == 1 ? 2.4 : slot == 2 ? -2.4 : 0.0;
    }

    private double companionOffsetZ(int slot) {
        return slot == 3 ? 2.4 : 0.0;
    }

    private String entityIdentifier(String species) {
        String normalized = species.toLowerCase(Locale.ROOT);
        if (normalized.equals("polarbear")) normalized = "polar_bear";
        return "minecraft:" + normalized;
    }

    private void summonTeam(Player player) {
        int summoned = 0;
        Map<Integer, Entity> companions = activeCompanions.computeIfAbsent(
                player.getUniqueId(), ignored -> new HashMap<>());
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            if (id.isEmpty() || !hasBeast(player, id)) continue;
            Entity existing = companions.get(slot);
            if (existing != null && !existing.isClosed()) {
                summoned++;
                continue;
            }
            String base = beastPath(player, id);
            String species = data.getString(base + ".species", "wolf").toLowerCase(Locale.ROOT);
            int lookIdx = getConfig().getBoolean("rarity-looks", true)
                    ? rarityIndex(data.getString(base + ".rarity", "common").toLowerCase(Locale.ROOT)) : -1;
            String modelSpecies = lookIdx >= 0 ? LOOK_ENTITY[lookIdx] : species;
            Entity pet;
            try {
                String customId = switch (modelSpecies) {
                    case "wolf" -> NexoraWolfEntity.IDENTIFIER;
                    case "fox" -> NexoraFoxEntity.IDENTIFIER;
                    case "cat" -> NexoraCatEntity.IDENTIFIER;
                    case "bee" -> NexoraBeeEntity.IDENTIFIER;
                    case "axolotl" -> NexoraAxolotlEntity.IDENTIFIER;
                    case "horse" -> NexoraHorseEntity.IDENTIFIER;
                    case "panda" -> NexoraPandaEntity.IDENTIFIER;
                    case "polarbear", "polar_bear" -> NexoraPolarBearEntity.IDENTIFIER;
                    case "rabbit" -> NexoraRabbitEntity.IDENTIFIER;
                    case "ocelot" -> NexoraOcelotEntity.IDENTIFIER;
                    case "turtle" -> NexoraTurtleEntity.IDENTIFIER;
                    case "dolphin" -> NexoraDolphinEntity.IDENTIFIER;
                    case "parrot" -> NexoraParrotEntity.IDENTIFIER;
                    default -> null;
                };
                String vanillaId = entityIdentifier(modelSpecies);
                if (customId != null) {
                    try {
                        pet = Entity.createEntity(customId, player);
                    } catch (RuntimeException customEx) {
                        getLogger().warning("Кастомная сущность " + species + " недоступна, включён ванильный fallback: " + customEx.getMessage());
                        pet = Entity.createEntity(vanillaId, player);
                    }
                    if (pet == null) pet = Entity.createEntity(vanillaId, player);
                } else {
                    pet = Entity.createEntity(vanillaId, player);
                }
            } catch (RuntimeException ex) {
                getLogger().warning("Не удалось создать питомца " + species + ": " + ex.getMessage());
                pet = null;
            }
            if (pet == null) {
                player.sendMessage("§cНе удалось призвать вид: " + species + ". Он пока не поддерживается сервером.");
                continue;
            }
            String rarity = data.getString(base + ".rarity", "common");
            if (lookIdx >= 0 && pet instanceof CustomEntity) {
                pet.setDataProperty(new IntEntityData(Entity.DATA_VARIANT, LOOK_VARIANT[lookIdx]));
            }
            pet.setNameTag(rarityPrefix(rarity) + " §f" + (lookIdx >= 0 ? LOOK_TITLES[lookIdx] : prettySpecies(species))
                    + " §8[§f" + data.getInt(base + ".level", 1) + "§8]");
            pet.setNameTagVisible(true);
            pet.setNameTagAlwaysVisible(true);
            boolean positioned = pet.teleport(player.add(companionOffsetX(slot), 0, companionOffsetZ(slot)));
            if (!positioned) {
                pet.close();
                player.sendMessage("§cНе удалось разместить питомца рядом с тобой: " + species);
                continue;
            }
            // Send the spawn packet explicitly to the owner as well as nearby players.
            // This avoids relying only on the chunk-player broadcast during the summon tick.
            pet.spawnToAll();
            if (player.isOnline()) pet.spawnTo(player);
            if (pet.isClosed()) {
                player.sendMessage("§cСервер закрыл сущность питомца сразу после создания: " + species);
                continue;
            }
            getLogger().info("Питомец создан: " + species + " (" + pet.getClass().getSimpleName()
                    + "), entityId=" + pet.getId() + ", owner=" + player.getName());
            companions.put(slot, pet);
            summoned++;
        }
        if (summoned == 0) {
            player.sendMessage("§eСначала добавь существ в активную команду.");
        } else {
            player.sendMessage("§a✦ Призвано питомцев: §f" + summoned + "§a/3. Они будут следовать за тобой.");
        }
    }

    /**
     * Vanilla mob AI must never turn a companion against its owner. Damage is already
     * cancelled in {@link #onCompanionProtectionAndDefense}; this also drops the angry
     * state and any target pointing at the owner so the pet does not growl or chase.
     * Done through reflection because the exact AI API differs between Nukkit-MOT builds.
     */
    private void calmCompanion(Entity pet, Player owner) {
        if (pet == null || pet.isClosed()) return;
        try {
            pet.getClass().getMethod("setAngry", boolean.class).invoke(pet, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // method not present on this entity type
        }
        try {
            Object current = pet.getClass().getMethod("getTarget").invoke(pet);
            if (current == owner) {
                try {
                    pet.getClass().getMethod("setTarget", Entity.class, boolean.class).invoke(pet, null, false);
                } catch (NoSuchMethodException e) {
                    pet.getClass().getMethod("setTarget", Entity.class).invoke(pet, (Object) null);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // no target API on this entity type
        }
    }

    private void recallTeam(Player player, boolean notify) {
        Map<Integer, Entity> companions = activeCompanions.remove(player.getUniqueId());
        int removed = 0;
        if (companions != null) {
            for (Entity pet : companions.values()) {
                if (pet != null && !pet.isClosed()) {
                    pet.close();
                    removed++;
                }
            }
        }
        if (notify) player.sendMessage("§eПитомцы отозваны в коллекцию. §7(" + removed + ")");
    }

    // -------------------- Existing text fallbacks --------------------

    private void showCollection(Player player) {
        Map<String, Object> entries = collection(player);
        player.sendMessage("§6§lКоллекция NEXORA §7(" + entries.size() + ")");
        if (entries.isEmpty()) {
            player.sendMessage("§7Коллекция пуста. Получи яйцо или приручи существо ловчей сферой.");
            return;
        }
        int shown = 0;
        for (String id : entries.keySet()) {
            String base = beastPath(player, id);
            String species = data.getString(base + ".species", "unknown");
            String rarity = data.getString(base + ".rarity", "common");
            int level = data.getInt(base + ".level", 1);
            player.sendMessage("§f" + id + " §8| " + rarityColor(rarity) + prettySpecies(species)
                    + " §7[" + rarityName(rarity) + "] §8Ур. " + level);
            if (++shown >= 30) {
                if (entries.size() > shown) player.sendMessage("§7Показаны первые 30. Используй /beasts info <ID>.");
                break;
            }
        }
        player.sendMessage("§7Активная команда: §f" + teamCount(player) + "/3");
    }

    private void showTeam(Player player) {
        player.sendMessage("§6§lАктивная команда §7(максимум 3)");
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            if (id.isEmpty() || !hasBeast(player, id)) {
                player.sendMessage("§e" + slot + ". §7Пусто");
            } else {
                String base = beastPath(player, id);
                String species = data.getString(base + ".species", "unknown");
                String rarity = data.getString(base + ".rarity", "common");
                player.sendMessage("§e" + slot + ". §f" + prettySpecies(species) + " §7[" + rarityColor(rarity)
                        + rarityName(rarity) + "§7] §8ID: " + id);
            }
        }
        player.sendMessage("§7Назначить: §f/beasts team <слот 1-3> <ID>");
    }

    private void setTeamSlot(Player player, String slotText, String id) {
        int slot;
        try { slot = Integer.parseInt(slotText); }
        catch (NumberFormatException ex) { player.sendMessage("§cСлот должен быть числом от 1 до 3."); return; }
        if (slot < 1 || slot > 3) { player.sendMessage("§cМожно использовать только слоты 1, 2 и 3."); return; }
        if (!hasBeast(player, id)) { player.sendMessage("§cТакого существа нет в твоей коллекции."); return; }
        for (int i = 1; i <= 3; i++) {
            if (i != slot && id.equals(data.getString(playerPath(player) + ".team." + i, ""))) {
                player.sendMessage("§cЭто существо уже стоит в слоте " + i + "."); return;
            }
        }
        Map<Integer, Entity> active = activeCompanions.get(player.getUniqueId());
        if (active != null) {
            Entity previous = active.remove(slot);
            if (previous != null && !previous.isClosed()) previous.close();
        }
        data.set(playerPath(player) + ".team." + slot, id);
        data.save();
        player.sendMessage("§aСущество назначено в слот команды " + slot + ".");
        if (active != null) {
            getServer().getScheduler().scheduleDelayedTask(this, () -> {
                if (player.isOnline()) summonTeam(player);
            }, 1);
        }
    }

    private void showBeastInfo(Player player, String id) {
        if (!hasBeast(player, id)) { player.sendMessage("§cСущество не найдено."); return; }
        String base = beastPath(player, id);
        String species = data.getString(base + ".species", "unknown");
        String rarity = data.getString(base + ".rarity", "common");
        player.sendMessage("§6§lИнформация о существе");
        player.sendMessage("§fВид: §b" + prettySpecies(species));
        player.sendMessage("§fРедкость: " + rarityColor(rarity) + rarityName(rarity));
        int level = data.getInt(base + ".level", 1);
        player.sendMessage("§fУровень: §a" + level);
        player.sendMessage("§fОпыт: §a" + data.getInt(base + ".xp", 0) + "§7 / " + xpToNext(level));
        player.sendMessage("§fЗдоровье: §c" + stat(base, "vitality", 20) + " §8| §fАтака: §c" + stat(base, "power", 5));
        player.sendMessage("§fЗащита: §b" + stat(base, "defense", 2) + " §8| §fПотенциал: §d" + data.getInt(base + ".potential", 0));
        player.sendMessage("§fПолучено: §7" + data.getString(base + ".source", "unknown"));
        player.sendMessage("§fID: §7" + id);
    }

    private void releaseBeast(Player player, String id) {
        if (!hasBeast(player, id)) { player.sendMessage("§cСущество не найдено."); return; }
        Map<Integer, Entity> companions = activeCompanions.get(player.getUniqueId());
        if (companions != null) {
            for (int slot = 1; slot <= 3; slot++) {
                if (id.equals(data.getString(playerPath(player) + ".team." + slot, ""))) {
                    Entity active = companions.remove(slot);
                    if (active != null && !active.isClosed()) active.close();
                }
            }
            if (companions.isEmpty()) activeCompanions.remove(player.getUniqueId());
        }
        String base = beastPath(player, id);
        data.remove(base);
        for (int slot = 1; slot <= 3; slot++) {
            if (id.equals(data.getString(playerPath(player) + ".team." + slot, ""))) {
                data.remove(playerPath(player) + ".team." + slot);
            }
        }
        data.save();
        player.sendMessage("§eСущество удалено из коллекции.");
    }

    private void giveOrb(CommandSender sender, String[] args) {
        Player target = getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cИгрок не найден или не в сети."); return; }
        int amount = 1;
        if (args.length >= 3) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ex) { sender.sendMessage("§cКоличество должно быть числом."); return; }
        }
        Item orb = Item.get(368, 0, amount);
        orb.setCustomName(ORB_NAME);
        orb.setLore("§7Нажми по существу, чтобы попытаться приручить его.", "§8NEXORA: BEASTS");
        target.getInventory().addItem(orb);
        sender.sendMessage("§aВыдано ловчих сфер: " + amount + " игроку " + target.getName() + ".");
    }

    private void giveEgg(CommandSender sender, String[] args) {
        Player target = getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cИгрок не найден или не в сети."); return; }
        String species = args[2];
        String rarity = args[3].toLowerCase(Locale.ROOT);
        if (!species.equalsIgnoreCase("random") && !SAFE_ID.matcher(species).matches()) {
            sender.sendMessage("§cВид должен содержать только латиницу, цифры, _ или -.");
            return;
        }
        if (!isRarity(rarity)) { sender.sendMessage("§cНеизвестная редкость. Используй: " + String.join(", ", RARITIES)); return; }
        target.getInventory().addItem(createEgg(species, rarity));
        sender.sendMessage("§aВыдано " + rarityColor(rarity) + rarityName(rarity) + "§a яйцо игроку " + target.getName() + ".");
    }

    private Item createEgg(String species, String rarity) {
        Item egg = Item.get(344, 0, 1);
        egg.setCustomName(EGG_PREFIX + rarityColor(rarity) + rarityName(rarity));
        egg.setLore(
                "§7Вид: §f" + species,
                "§7Редкость: " + rarityColor(rarity) + rarityName(rarity),
                "§eНажми ПКМ, чтобы вылупить существо",
                "§0NEXORA_SPECIES:" + species,
                "§0NEXORA_RARITY:" + rarity,
                "§8NEXORA: BEASTS"
        );
        return egg;
    }

    private void giveEggShowcase(CommandSender sender, String playerName) {
        Player target = getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден или не в сети.");
            return;
        }
        for (String rarity : RARITIES) {
            target.getInventory().addItem(createEgg("random", rarity));
        }
        sender.sendMessage("§aВыдан набор из 10 яиц NEXORA игроку " + target.getName() + ".");
        target.sendMessage("§6Тебе выдан набор для просмотра всех 10 редкостей яиц NEXORA!");
    }

    private void showEggCatalog(CommandSender sender) {
        sender.sendMessage("§6§lЯйца NEXORA — каталог редкостей");
        for (int i = 0; i < RARITIES.length; i++) {
            sender.sendMessage("§8• " + RARITY_COLORS[i] + RARITY_NAMES[i] + " §7(" + RARITIES[i] + ")");
        }
        sender.sendMessage("§7Администратор может выдать набор: §f/beasts giveeggs <игрок>");
    }

    private String rollEggSpecies(String rarity, Player player) {
        String[] pool = switch (rarity) {
            case "common" -> new String[]{"Wolf", "Rabbit", "Cat"};
            case "uncommon" -> new String[]{"Fox", "Parrot", "Ocelot"};
            case "rare" -> new String[]{"Bee", "Turtle", "Dolphin"};
            case "epic" -> new String[]{"Axolotl", "Panda", "Horse"};
            case "legendary" -> new String[]{"Wolf", "Fox", "Axolotl", "Panda"};
            case "mythic" -> new String[]{"Bee", "Dolphin", "Horse", "PolarBear"};
            case "ancient" -> new String[]{"PolarBear", "Panda", "Axolotl", "Turtle"};
            case "divine" -> new String[]{"Horse", "Dolphin", "Parrot", "PolarBear"};
            case "forbidden" -> new String[]{"Fox", "Bee", "Ocelot", "PolarBear"};
            case "absolute" -> new String[]{"Wolf", "Axolotl", "Panda", "PolarBear"};
            default -> RANDOM_SPECIES;
        };
        String previous = data.getString(playerPath(player) + ".lastEggSpecies", "");
        List<String> choices = new ArrayList<>();
        for (String candidate : pool) {
            if (!candidate.equalsIgnoreCase(previous)) choices.add(candidate);
        }
        if (choices.isEmpty()) choices.addAll(List.of(pool));
        String chosen = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        data.set(playerPath(player) + ".lastEggSpecies", chosen);
        data.save();
        return chosen;
    }

    private String addBeast(Player player, String species, String rarity, String source) {
        String id;
        do { id = UUID.randomUUID().toString().substring(0, 8); }
        while (hasBeast(player, id));
        String base = beastPath(player, id);
        data.set(base + ".species", species);
        data.set(base + ".rarity", rarity);
        data.set(base + ".level", 1);
        data.set(base + ".xp", 0);
        data.set(base + ".upgradePoints", 0);
        data.set(base + ".ultimateLevel", 0);
        data.set(base + ".bonus.vitality", 0);
        data.set(base + ".bonus.power", 0);
        data.set(base + ".bonus.defense", 0);
        data.set(base + ".source", source);
        data.set(base + ".potential", ThreadLocalRandom.current().nextInt(0, 6));
        data.set(base + ".created", System.currentTimeMillis());
        data.save();
        return id;
    }

    private void openBeastUpgradeMenu(Player player, String id) {
        if (!hasBeast(player, id)) { openCollectionMenu(player); return; }
        String base = beastPath(player, id);
        int points = data.getInt(base + ".upgradePoints", 0);
        FormWindowSimple form = new FormWindowSimple("§l§0РАЗВИТИЕ ПИТОМЦА",
                "§7Свободные очки: §e" + points
                + "\n\n§c❤ §fЖивучесть: §c" + stat(base, "vitality", 20)
                + "\n§6⚔ §fСила: §6" + stat(base, "power", 5)
                + "\n§b⬟ §fЗащита: §b" + stat(base, "defense", 2)
                + "\n\n§8Живучесть и защита уменьшают входящий урон. Сила повышает урон питомца.");
        form.addButton(new ElementButton("§l§c❤ ЖИВУЧЕСТЬ\n§8+4 к живучести за очко"));
        form.addButton(new ElementButton("§l§6⚔ СИЛА\n§8+2 к урону за очко"));
        form.addButton(new ElementButton("§l§b⬟ ЗАЩИТА\n§8+2 к защите за очко"));
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            switch (form.getResponse().getClickedButtonId()) {
                case 0 -> upgradeBeastStat(player, id, "vitality");
                case 1 -> upgradeBeastStat(player, id, "power");
                case 2 -> upgradeBeastStat(player, id, "defense");
                default -> { openBeastCard(player, id); return; }
            }
            openBeastUpgradeMenu(player, id);
        }));
        player.showFormWindow(form);
    }

    private void openUltimateUpgradeMenu(Player player, String id) {
        if (!hasBeast(player, id)) { openCollectionMenu(player); return; }
        String base = beastPath(player, id);
        int level = Math.max(0, Math.min(5, data.getInt(base + ".ultimateLevel", 0)));
        int points = data.getInt(base + ".upgradePoints", 0);
        String species = data.getString(base + ".species", "wolf").toLowerCase(Locale.ROOT);
        FormWindowSimple form = new FormWindowSimple("§l§5УЛУЧШЕНИЕ ПРОБУЖДЕНИЯ",
                "§7Ульта: §d" + ultimateName(species)
                + "\n§7Текущий уровень: §f" + level + "/5"
                + "\n§7Очки развития: §e" + points
                + "\n\n§aКаждый уровень: +15% урона, +0.5 блока радиуса и -2 сек. перезарядки."
                + "\n§8Стоимость следующего уровня: 2 очка развития."
                + (level >= 5 ? "\n\n§6Максимальный уровень достигнут." : ""));
        if (level < 5) form.addButton(new ElementButton("§d✹ УЛУЧШИТЬ ДО УР. " + (level + 1) + "\n§8Потратить 2 очка"));
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected == 0 && level < 5) {
                int currentPoints = data.getInt(base + ".upgradePoints", 0);
                int currentLevel = data.getInt(base + ".ultimateLevel", 0);
                if (currentPoints >= 2 && currentLevel < 5) {
                    data.set(base + ".upgradePoints", currentPoints - 2);
                    data.set(base + ".ultimateLevel", currentLevel + 1);
                    data.save();
                    player.sendMessage("§d✹ Пробуждение усилено до уровня " + (currentLevel + 1) + "!");
                } else {
                    player.sendMessage("§cНужно 2 очка развития.");
                }
                openUltimateUpgradeMenu(player, id);
            } else openBeastCard(player, id);
        }));
        player.showFormWindow(form);
    }

    private void upgradeBeastStat(Player player, String id, String stat) {
        if (!hasBeast(player, id)) return;
        String base = beastPath(player, id);
        int points = data.getInt(base + ".upgradePoints", 0);
        if (points <= 0) {
            player.sendMessage("§cНет свободных очков развития. Получай боевой опыт, чтобы повышать уровень.");
            return;
        }
        if (!List.of("vitality", "power", "defense").contains(stat)) return;
        data.set(base + ".upgradePoints", points - 1);
        data.set(base + ".bonus." + stat, data.getInt(base + ".bonus." + stat, 0)
                + (stat.equals("vitality") ? 4 : 2));
        data.save();
        player.sendMessage("§aХарактеристика питомца улучшена!");
    }

    private void gainBeastXp(Player owner, String id, int amount) {
        if (amount <= 0 || !hasBeast(owner, id)) return;
        String base = beastPath(owner, id);
        int oldLevel = Math.max(1, data.getInt(base + ".level", 1));
        if (oldLevel >= 100) return;
        int level = oldLevel;
        int xp = data.getInt(base + ".xp", 0) + amount;
        int gainedLevels = 0;
        while (level < 100 && xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            gainedLevels++;
        }
        if (level >= 100) xp = 0;
        int milestoneRewards = 0;
        int[] milestones = {10, 25, 50, 75, 100};
        for (int milestone : milestones) {
            if (oldLevel < milestone && level >= milestone) milestoneRewards += 2;
        }
        data.set(base + ".level", level);
        data.set(base + ".xp", xp);
        if (gainedLevels > 0 || milestoneRewards > 0) {
            int reward = gainedLevels + milestoneRewards;
            data.set(base + ".upgradePoints", data.getInt(base + ".upgradePoints", 0) + reward);
            owner.sendMessage("§6§lПИТОМЕЦ СТАЛ СИЛЬНЕЕ! §e" + prettySpecies(data.getString(base + ".species", "unknown"))
                    + " §7получил уровень " + level + " и §e+" + reward + " §7очк. развития.");
        }
        if (milestoneRewards > 0) {
            owner.sendMessage("§d✦ ЭВОЛЮЦИЯ! §f" + prettySpecies(data.getString(base + ".species", "unknown"))
                    + " §7достиг стадии «" + evolutionRank(level) + "» и получил §e+" + milestoneRewards
                    + " §7доп. очка развития!");
        }
        data.save();
    }

    private String evolutionRank(int level) {
        if (level >= 100) return "Абсолютный зверь";
        if (level >= 75) return "Пробуждённый";
        if (level >= 50) return "Вознесённый";
        if (level >= 25) return "Закалённый";
        if (level >= 10) return "Юный боец";
        return "Детёныш";
    }

    private int xpToNext(int level) { return 50 + (Math.max(1, level) * 50); }

    private int rarityIndex(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return i;
        return 0;
    }

    private int stat(String base, String key, int fallback) {
        int level = Math.max(1, data.getInt(base + ".level", 1));
        int tier = rarityIndex(data.getString(base + ".rarity", "common"));
        int potential = data.getInt(base + ".potential", 0);
        int baseStat = switch (key) {
            case "vitality" -> 20 + tier * 8 + (level - 1) * 4 + potential * 2;
            case "power" -> 5 + tier * 3 + (level - 1) * 2 + potential;
            case "defense" -> 2 + tier * 2 + (level - 1) + potential / 2;
            default -> fallback;
        };
        return baseStat + data.getInt(base + ".bonus." + key, 0);
    }

    private String stripColors(String value) {
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private String rollWildRarity() {
        int roll = ThreadLocalRandom.current().nextInt(1000);
        int sum = 0;
        for (int i = 0; i < WILD_RARITY_WEIGHTS.length; i++) {
            sum += WILD_RARITY_WEIGHTS[i];
            if (roll < sum) return RARITIES[i];
        }
        return "common";
    }

    private void consumeOne(Player player, Item item) {
        int count = item.getCount();
        if (count <= 1) player.getInventory().setItemInHand(Item.get(0, 0, 0));
        else {
            item.setCount(count - 1);
            player.getInventory().setItemInHand(item);
        }
    }

    private Map<String, Object> collection(Player player) {
        return data.getSection(playerPath(player) + ".collection");
    }

    private boolean hasBeast(Player player, String id) {
        return data.exists(beastPath(player, id) + ".species");
    }

    private int collectionSize(Player player) { return collection(player).size(); }

    private int teamCount(Player player) {
        int count = 0;
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            if (!id.isEmpty() && hasBeast(player, id)) count++;
        }
        return count;
    }

    private String playerPath(Player player) { return "players." + player.getUniqueId().toString(); }
    private String beastPath(Player player, String id) { return playerPath(player) + ".collection." + id; }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        sender.sendMessage("§cЭта команда доступна только игроку.");
        return false;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.isOp() || sender.hasPermission("nexora.beasts.admin")) return true;
        sender.sendMessage("§cНедостаточно прав.");
        return false;
    }

    private boolean isRarity(String rarity) {
        for (String value : RARITIES) if (value.equals(rarity)) return true;
        return false;
    }

    private String rarityName(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return RARITY_NAMES[i];
        return RARITY_NAMES[0];
    }

    private String rarityColor(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return RARITY_COLORS[i];
        return "§7";
    }

    /**
     * Bedrock-safe stepped gradient for the pet's rarity prefix.
     * Uses legacy Bedrock palette codes instead of Java-only RGB formatting.
     */
    /**
     * Uses per-letter Bedrock bitmap glyphs from the NEXORA resource pack.
     * The matching glyph sheet is generated by resource-pack/build_pack.py.
     * Each rarity label has its own sequence so the yellow-to-orange gradient
     * is baked into every individual character and never animates.
     */
    private String rarityPrefix(String rarity) {
        int rarityIndex = 0;
        for (int i = 0; i < RARITIES.length; i++) {
            if (RARITIES[i].equalsIgnoreCase(rarity)) { rarityIndex = i; break; }
        }
        String label = RARITY_NAMES[rarityIndex].toUpperCase(Locale.ROOT);
        if (getConfig().getBoolean("gradient-glyphs", false)) {
            // Gradient lettering from the NEXORA resource pack (font/glyph_E2.png).
            int offset = 0;
            for (int i = 0; i < rarityIndex; i++) offset += RARITY_NAMES[i].length();
            StringBuilder glyphs = new StringBuilder("§f");
            for (int j = 0; j < label.length(); j++) glyphs.append((char) (GLYPH_BASE + offset + j));
            return glyphs.append("§r").toString();
        }
        // Use native Bedrock formatting instead of custom PUA glyphs: unsupported
        // glyph atlases can inflate name tags dramatically on mobile clients.
        String[] gradient = switch (rarityIndex) {
            case 1 -> new String[]{"§a", "§2"};
            case 2 -> new String[]{"§b", "§3"};
            case 3 -> new String[]{"§d", "§5"};
            case 4 -> new String[]{"§e", "§6", "§c"};
            case 5 -> new String[]{"§d", "§c"};
            case 6 -> new String[]{"§b", "§3"};
            case 7 -> new String[]{"§f", "§e"};
            case 8 -> new String[]{"§c", "§4"};
            case 9 -> new String[]{"§f", "§d"};
            default -> new String[]{"§7"};
        };
        StringBuilder result = new StringBuilder("§l✦ ");
        int index = 0;
        int length = Math.max(1, label.codePointCount(0, label.length()));
        for (int codePoint : label.codePoints().toArray()) {
            int color = Math.min(gradient.length - 1, index * gradient.length / length);
            result.append(gradient[color]).appendCodePoint(codePoint);
            index++;
        }
        return result.append("§r").toString();
    }

    private String normalizeSpecies(String species) {
        String value = species.trim().replace(' ', '_').toLowerCase(Locale.ROOT);
        return value.isEmpty() ? "unknown" : value;
    }

    private String prettySpecies(String species) {
        if (species.equalsIgnoreCase("polarbear")) return "Polar Bear";
        String value = species.replace('_', ' ');
        if (value.isEmpty()) return "Неизвестное существо";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lNEXORA: BEASTS");
        sender.sendMessage("§e/beasts eggs §7— каталог 10 редкостей яиц");
        sender.sendMessage("§e/beasts collection §7— открыть коллекцию");
        sender.sendMessage("§e/beasts team §7— показать активную команду");
        sender.sendMessage("§e/beasts team <1-3> <ID> §7— назначить существо");
        sender.sendMessage("§e/beasts summon §7— призвать активную команду");
        sender.sendMessage("§e/beasts recall §7— отозвать питомцев");
        sender.sendMessage("§d/beasts ultimate <1-3> §7— ульта призванного питомца (45 сек.)");
        sender.sendMessage("§e/beasts info <ID> §7— информация о существе");
        sender.sendMessage("§e/beasts upgrade <ID> §7— распределить очки развития питомца");
        sender.sendMessage("§e/beasts release <ID> §7— отпустить существо");
        if (sender.isOp() || sender.hasPermission("nexora.beasts.admin")) {
            sender.sendMessage("§c/beasts giveorb <игрок> [количество] §7— выдать сферы");
            sender.sendMessage("§c/beasts giveegg <игрок> <вид|random> <редкость> §7— выдать яйцо");
            sender.sendMessage("§c/beasts giveeggs <игрок> §7— выдать набор из 10 яиц");
        }
    }
}
