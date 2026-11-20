package com.cosmetics.manager;

import org.bukkit.Particle;
import org.bukkit.event.EventHandler;
import com.cosmetics.CosmeticsPlugin;
import com.cosmetics.cosmetic.PetCosmetic;
import com.cosmetics.cosmetic.CompanionCosmetic;
import com.cosmetics.util.ItemUtil;
import com.cosmetics.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PetManager {

    private final CosmeticsPlugin plugin;
    private final Map<UUID, Entity> activePets = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> activeCompanions = new ConcurrentHashMap<>();
    private final Map<UUID, Location> companionLocations = new ConcurrentHashMap<>();
    // For companions with multiple entities (like Gudodama's 7 spheres)
    private final Map<UUID, List<Entity>> multiEntityCompanions = new ConcurrentHashMap<>();

    // Gudodama-specific: track player states (shift/attack)
    private final Map<UUID, Boolean> gudodamaShiftState = new ConcurrentHashMap<>();
    private final Map<UUID, Long> gudodamaLastAttack = new ConcurrentHashMap<>();

    public PetManager(CosmeticsPlugin plugin) {
        this.plugin = plugin;
        registerGudodamaListeners();
    }

    public void spawnPet(Player player, PetCosmetic cosmetic) {
        despawnPet(player);

        EntityType entityType = cosmetic.getEntityType();
        Location spawnLoc = player.getLocation().add(1, 0, 1);

        Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, entityType);
        if (entity instanceof Animals animal) {
            configurePet(animal, cosmetic, player);
        } else if (entity instanceof Tameable tameable) {
            tameable.setOwner(player);
            configurePet((Animals) entity, cosmetic, player);
        } else if (entity instanceof LivingEntity living) {
            configurePet(living, cosmetic, player);
        }

        activePets.put(player.getUniqueId(), entity);
    }

    private void configurePet(Animals animal, PetCosmetic cosmetic, Player owner) {
        animal.setCustomName(MessageUtil.color(cosmetic.getName()));
        animal.setCustomNameVisible(true);
        animal.setRemoveWhenFarAway(false);
        animal.setInvulnerable(true);
        animal.setCollidable(false);
        animal.setGravity(true);
        animal.setSilent(true);

        if (animal instanceof Tameable tameable) {
            tameable.setOwner(owner);
        }

        if (animal instanceof Ageable ageable) {
            ageable.setAdult();
        }

        String variant = cosmetic.getVariant();
        if (animal instanceof Cat cat && !variant.isEmpty()) {
            try {
                cat.setCatType(Cat.Type.valueOf(variant.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        } else if (animal instanceof Fox fox && !variant.isEmpty()) {
            try {
                fox.setFoxType(Fox.Type.valueOf(variant.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        } else if (animal instanceof Wolf wolf && !variant.isEmpty()) {
            try {
                var wolfVariant = Registry.WOLF_VARIANT.get(NamespacedKey.minecraft(variant.toLowerCase()));
                if (wolfVariant != null) {
                    wolf.setVariant(wolfVariant);
                }
            } catch (IllegalArgumentException ignored) {}
        } else if (animal instanceof Rabbit rabbit && !variant.isEmpty()) {
            try {
                rabbit.setRabbitType(Rabbit.Type.valueOf(variant.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        } else if (animal instanceof Panda panda && !variant.isEmpty()) {
            try {
                panda.setMainGene(org.bukkit.entity.Panda.Gene.valueOf(variant.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        } else if (animal instanceof Axolotl axolotl && !variant.isEmpty()) {
            try {
                axolotl.setVariant(Axolotl.Variant.valueOf(variant.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }

        double scale = cosmetic.getScale();
        if (scale != 1.0 && animal instanceof LivingEntity living) {
            living.getAttribute(Attribute.SCALE).setBaseValue(scale);
        }
    }

    private void configurePet(LivingEntity entity, PetCosmetic cosmetic, Player owner) {
        entity.setCustomName(MessageUtil.color(cosmetic.getName()));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setInvulnerable(true);
        entity.setCollidable(false);
        entity.setGravity(true);
        entity.setSilent(true);
        entity.setAI(false);

        double scale = cosmetic.getScale();
        if (scale != 1.0) {
            entity.getAttribute(Attribute.SCALE).setBaseValue(scale);
        }
    }

    public void despawnPet(Player player) {
        Entity entity = activePets.remove(player.getUniqueId());
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    public void spawnCompanion(Player player, CompanionCosmetic cosmetic) {
        despawnCompanion(player.getUniqueId());

        Location spawnLoc = player.getLocation().add(0, 1.5, 0);

        // Special handling for Gudodama companion
        if ("gudodama".equals(cosmetic.getId())) {
            spawnGudodamaCompanion(player, cosmetic, spawnLoc);
            return;
        }

        // Check if companion uses a custom ItemsAdder item (ItemDisplay)
        if (cosmetic.usesCustomItemDisplay()) {
            spawnItemDisplayCompanion(player, cosmetic, spawnLoc);
        } else {
            spawnEntityCompanion(player, cosmetic, spawnLoc);
        }
    }

    private void spawnEntityCompanion(Player player, CompanionCosmetic cosmetic, Location spawnLoc) {
        EntityType entityType = cosmetic.getEntityType();

        Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, entityType);
        configureCompanion(entity, cosmetic);

        activeCompanions.put(player.getUniqueId(), entity);
        companionLocations.put(player.getUniqueId(), entity.getLocation());
    }

    private void spawnItemDisplayCompanion(Player player, CompanionCosmetic cosmetic, Location spawnLoc) {
        ItemStack displayItem = cosmetic.getDisplayItem();
        if (displayItem == null || displayItem.getType() == Material.AIR) {
            plugin.getLogger().warning("Companion " + cosmetic.getId() + " has invalid display item, falling back to entity");
            spawnEntityCompanion(player, cosmetic, spawnLoc);
            return;
        }

        try {
            // Spawn ITEM_DISPLAY entity (available in 1.20.4+)
            Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ITEM_DISPLAY);
            if (entity != null) {
                configureItemDisplay(entity, displayItem, cosmetic);
                activeCompanions.put(player.getUniqueId(), entity);
                companionLocations.put(player.getUniqueId(), entity.getLocation());
            } else {
                plugin.getLogger().warning("Failed to spawn ITEM_DISPLAY for companion " + cosmetic.getId());
                spawnEntityCompanion(player, cosmetic, spawnLoc);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn ItemDisplay for companion " + cosmetic.getId() + ": " + e.getMessage()
                    + " (requires Paper 1.20.4+). Falling back to entity.");
            spawnEntityCompanion(player, cosmetic, spawnLoc);
        }
    }

    /**
     * Spawns the exclusive Gudodama companion - 7 black BlockDisplay spheres in a semi-circular arc behind the player.
     * Spec:
     * - 7 BlockDisplay spheres using BLACK_CONCRETE
     * - Translation offset: -0.14f
     * - Scale: 0.28f
     * - Teleport duration: 2 ticks
     * - Glow color: RGB(45, 0, 80) - purple
     * - Semi-circular arc behind player following yaw
     * - Sin wave animation + SMOKE/SQUID_INK particles
     * - Shift: 7th sphere becomes staff in hand (scale 0.08, 1.8, 0.08), others compress
     * - Attack: sphere shoots at target with WITHER_SHOOT sound, returns
     */
    private void spawnGudodamaCompanion(Player player, CompanionCosmetic cosmetic, Location spawnLoc) {
        List<Entity> spheres = new CopyOnWriteArrayList<>();

        try {
            // Get properties from cosmetic config
            int sphereCount = cosmetic.getPropertyInt("gudodama-spheres", 7);
            double innerRadius = cosmetic.getPropertyDouble("gudodama-inner-radius", 0.5);
            double outerRadius = cosmetic.getPropertyDouble("gudodama-outer-radius", 1.5);
            String blockMaterialStr = cosmetic.getProperty("gudodama-block-material", "BLACK_CONCRETE");
            String glowColorStr = cosmetic.getProperty("gudodama-glow-color", "2D0050"); // RGB(45, 0, 80)

            Material blockMaterial;
            try {
                blockMaterial = Material.valueOf(blockMaterialStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                blockMaterial = Material.BLACK_CONCRETE;
            }

            // Parse glow color - RGB(45, 0, 80) = #2D0050
            org.bukkit.Color glowColor;
            try {
                String hex = glowColorStr.replace("#", "");
                if (hex.length() == 6) {
                    int r = Integer.parseInt(hex.substring(0, 2), 16);
                    int g = Integer.parseInt(hex.substring(2, 4), 16);
                    int b = Integer.parseInt(hex.substring(4, 6), 16);
                    glowColor = org.bukkit.Color.fromRGB(r, g, b);
                } else {
                    glowColor = org.bukkit.Color.fromRGB(45, 0, 80); // Purple RGB(45, 0, 80)
                }
            } catch (Exception e) {
                glowColor = org.bukkit.Color.fromRGB(45, 0, 80);
            }

            // Player's look direction (for positioning behind)
            Vector direction = player.getLocation().getDirection().normalize();
            Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
            Vector up = new Vector(0, 1, 0);

            // Create BlockDisplay for each sphere in a semi-circular arc behind the player
            for (int i = 0; i < sphereCount; i++) {
                try {
                    // Calculate angle for semi-circle (-90 to +90 degrees, i.e., behind the player)
                    double angle = Math.toRadians(-90 + (180.0 * i / Math.max(1, sphereCount - 1)));

                    // Position in a semi-circular arc behind the player
                    double radius = innerRadius + (outerRadius - innerRadius) * (i % 2 == 0 ? 0.3 : 0.7);
                    Vector offset = right.clone().multiply(Math.cos(angle) * radius)
                        .add(up.clone().multiply(Math.sin(angle) * radius * 0.5 + 0.5))
                        .add(direction.clone().multiply(-1.0)); // Behind player

                    Location sphereLoc = spawnLoc.clone().add(offset);

                    Entity entity = sphereLoc.getWorld().spawnEntity(sphereLoc, EntityType.BLOCK_DISPLAY);
                    if (entity instanceof BlockDisplay blockDisplay) {
                        // Set the block state (black concrete) directly without reflection
                        BlockData blockData = blockMaterial.createBlockData();
                        blockDisplay.setBlock(blockData);

                        // Configure BlockDisplay
                        configureBlockDisplay(blockDisplay, cosmetic);

                        // Set glow color - RGB(45, 0, 80) purple
                        blockDisplay.setGlowColorOverride(glowColor);
                        blockDisplay.setGlowing(true);

                        // Translation offset: -0.14f along Y axis for slight hover
                        Transformation transform = blockDisplay.getTransformation();
                        Vector3f translation = new Vector3f(0, -0.14f, 0);
                        blockDisplay.setTransformation(new Transformation(
                            translation,
                            transform.getLeftRotation(),
                            transform.getScale(),
                            transform.getRightRotation()
                        ));

                        // Scale: 0.28f
                        Vector3f scaleVec = new Vector3f(0.28f, 0.28f, 0.28f);
                        blockDisplay.setTransformation(new Transformation(
                            transform.getTranslation(),
                            transform.getLeftRotation(),
                            scaleVec,
                            transform.getRightRotation()
                        ));

                        // Teleport duration: 2 ticks
                        blockDisplay.setTeleportDuration(2);
                        blockDisplay.setInterpolationDuration(2);

                        spheres.add(entity);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to spawn Gudodama sphere " + i + ": " + e.getMessage());
                }
            }

            if (!spheres.isEmpty()) {
                // Store the first sphere as the main companion for tracking
                activeCompanions.put(player.getUniqueId(), spheres.get(0));
                companionLocations.put(player.getUniqueId(), spawnLoc);
                // Store all spheres for multi-entity management
                multiEntityCompanions.put(player.getUniqueId(), spheres);

                // Apply common companion settings to all spheres
                for (Entity sphere : spheres) {
                    configureCompanion(sphere, cosmetic);
                }
            } else {
                plugin.getLogger().warning("Failed to spawn any Gudodama spheres, falling back to entity companion");
                spawnEntityCompanion(player, cosmetic, spawnLoc);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn Gudodama companion: " + e.getMessage());
            spawnEntityCompanion(player, cosmetic, spawnLoc);
        }
    }

    /**
     * Configures BlockDisplay entity with common settings.
     */
    private void configureBlockDisplay(BlockDisplay blockDisplay, CompanionCosmetic cosmetic) {
        try {

            // setBrightness
            blockDisplay.setBrightness(new BlockDisplay.Brightness(15, 15));

            // setShadowRadius, setShadowStrength
            blockDisplay.setShadowRadius(0f);
            blockDisplay.setShadowStrength(0f);

            // setInterpolationDuration, setTeleportDuration
            blockDisplay.setInterpolationDuration(0);
            blockDisplay.setTeleportDuration(0);

            // setScale via Transformation (for Display entities)
            double scale = cosmetic.getScale();
            if (scale != 1.0) {
                Transformation transformation = blockDisplay.getTransformation();
                Vector3f scaleVec = new Vector3f((float) scale, (float) scale, (float) scale);
                blockDisplay.setTransformation(new Transformation(
                    transformation.getTranslation(),
                    transformation.getLeftRotation(),
                    scaleVec,
                    transformation.getRightRotation()
                ));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to configure BlockDisplay: " + e.getMessage());
        }
    }

    private void configureItemDisplay(Entity entity, ItemStack displayItem, CompanionCosmetic cosmetic) {
        try {
            if (entity instanceof ItemDisplay itemDisplay) {
                // setItemStack
                itemDisplay.setItemStack(displayItem.clone());

                // setItemDisplayTransform
                itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);

                // setBrightness
                itemDisplay.setBrightness(new ItemDisplay.Brightness(15, 15));

                // setShadowRadius, setShadowStrength
                itemDisplay.setShadowRadius(0f);
                itemDisplay.setShadowStrength(0f);

                // setInterpolationDuration, setTeleportDuration
                itemDisplay.setInterpolationDuration(0);
                itemDisplay.setTeleportDuration(0);

                // setScale via Transformation (for Display entities)
                double scale = cosmetic.getScale();
                if (scale != 1.0) {
                    Transformation transformation = itemDisplay.getTransformation();
                    Vector3f scaleVec = new Vector3f((float) scale, (float) scale, (float) scale);
                    itemDisplay.setTransformation(new Transformation(
                        transformation.getTranslation(),
                        transformation.getLeftRotation(),
                        scaleVec,
                        transformation.getRightRotation()
                    ));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to configure ItemDisplay for companion " + cosmetic.getId() + ": " + e.getMessage());
        }

        // Apply common companion settings
        configureCompanion(entity, cosmetic);
    }

    private void configureCompanion(Entity entity, CompanionCosmetic cosmetic) {
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setCollidable(false);
            living.setAI(false);
        }

        entity.setCustomName(MessageUtil.color(cosmetic.getName()));
        entity.setCustomNameVisible(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setInvisible(false);
        entity.setGlowing(true);

        double scale = cosmetic.getScale();
        if (entity instanceof LivingEntity living && scale != 1.0) {
            living.getAttribute(Attribute.SCALE).setBaseValue(scale);
        } else if (entity instanceof Display display && scale != 1.0) {
            Transformation transformation = display.getTransformation();
            Vector3f scaleVec = new Vector3f((float) scale, (float) scale, (float) scale);
            display.setTransformation(new Transformation(
                transformation.getTranslation(),
                transformation.getLeftRotation(),
                scaleVec,
                transformation.getRightRotation()
            ));
        }
    }

    public void despawnCompanion(UUID playerId) {
        // Remove main companion entity
        Entity entity = activeCompanions.remove(playerId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        // Remove all multi-entity companions (e.g., Gudodama spheres)
        List<Entity> multiEntities = multiEntityCompanions.remove(playerId);
        if (multiEntities != null) {
            for (Entity e : multiEntities) {
                if (e != null && e.isValid() && e != entity) { // Avoid double-removing the main entity
                    e.remove();
                }
            }
        }
        companionLocations.remove(playerId);
    }

    public void updateCompanionLocation(UUID playerId, Location location) {
        companionLocations.put(playerId, location);
        // Update main companion
        Entity entity = activeCompanions.get(playerId);
        if (entity != null && entity.isValid()) {
            entity.teleport(location);
        }
        // Update all multi-entity companions
        List<Entity> multiEntities = multiEntityCompanions.get(playerId);
        if (multiEntities != null) {
            for (Entity e : multiEntities) {
                if (e != null && e.isValid() && e != entity) {
                    e.teleport(location);
                }
            }
        }
    }

    public void updateGudodamaAnimations(UUID playerId, Player player) {
        List<Entity> spheres = multiEntityCompanions.get(playerId);
        if (spheres == null || spheres.isEmpty()) return;

        boolean isShifting = player.isSneaking();
        boolean wasShifting = gudodamaShiftState.getOrDefault(playerId, false);
        gudodamaShiftState.put(playerId, isShifting);

        // Get player direction and vectors
        Vector direction = player.getLocation().getDirection().normalize();
        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        Vector up = new Vector(0, 1, 0);

        long currentTime = System.currentTimeMillis();
        double time = currentTime * 0.002; // Animation speed

        for (int i = 0; i < spheres.size(); i++) {
            Entity sphere = spheres.get(i);
            if (!(sphere instanceof BlockDisplay blockDisplay) || !sphere.isValid()) continue;

            try {
                // Base angle for semi-circle
                double baseAngle = Math.toRadians(-90 + (180.0 * i / Math.max(1, spheres.size() - 1)));
                double innerRadius = 0.5;
                double outerRadius = 1.5;
                double radius = innerRadius + (outerRadius - innerRadius) * (i % 2 == 0 ? 0.3 : 0.7);

                // Sin wave vertical animation
                double wave = Math.sin(time + i * 0.8) * 0.3;

                if (isShifting) {
                    // SHIFT MODE: 7th sphere (index 6) becomes staff in hand, others compress
                    if (i == spheres.size() - 1) { // 7th sphere (index 6)
                        // Transform to staff in hand
                        Location handLoc = player.getLocation().add(0, 1.5, 0).add(direction.clone().multiply(1.5)).add(right.clone().multiply(-0.3));
                        // Staff scale: 0.08, 1.8, 0.08 (thin vertical rod)
                        Transformation transform = blockDisplay.getTransformation();
                        Vector3f staffScale = new Vector3f(0.08f, 1.8f, 0.08f);
                        blockDisplay.setTransformation(new Transformation(
                            transform.getTranslation(),
                            transform.getLeftRotation(),
                            staffScale,
                            transform.getRightRotation()
                        ));
                        // Teleport to hand position
                        blockDisplay.teleport(handLoc);
                    } else {
                        // Other spheres compress toward center
                        double compressedRadius = radius * 0.3;
                        Vector offset = right.clone().multiply(Math.cos(baseAngle) * compressedRadius)
                            .add(up.clone().multiply(Math.sin(baseAngle) * compressedRadius * 0.5 + 0.5))
                            .add(direction.clone().multiply(-0.5));
                        Location sphereLoc = player.getLocation().add(0, 1.5, 0).add(offset);
                        blockDisplay.teleport(sphereLoc);
                    }
                } else {
                    // NORMAL MODE: semi-circular arc with sin wave
                    if (wasShifting && !isShifting) {
                        // Returning from shift - restore normal scale
                        Transformation transform = blockDisplay.getTransformation();
                        Vector3f normalScale = new Vector3f(0.28f, 0.28f, 0.28f);
                        blockDisplay.setTransformation(new Transformation(
                            transform.getTranslation(),
                            transform.getLeftRotation(),
                            normalScale,
                            transform.getRightRotation()
                        ));
                    }

                    Vector offset = right.clone().multiply(Math.cos(baseAngle) * radius)
                        .add(up.clone().multiply(Math.sin(baseAngle) * radius * 0.5 + 0.5 + wave))
                        .add(direction.clone().multiply(-1.0)); // Behind player

                    Location sphereLoc = player.getLocation().add(0, 1.5, 0).add(offset);
                    blockDisplay.teleport(sphereLoc);
                }

                // Spawn particles: SMOKE + SQUID_INK around spheres
                if (Math.random() < 0.3) { // 30% chance per tick per sphere
                    Location particleLoc = sphere.getLocation();
                    getParticleManager().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.1, 0.1, 0.1, 0.02);
                    getParticleManager().spawnParticle(Particle.SQUID_INK, particleLoc, 1, 0.1, 0.1, 0.1, 0.02);
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update Gudodama sphere " + i + ": " + e.getMessage());
            }
        }
    }

    private void registerGudodamaListeners() {
        // EntityDamageByEntityEvent - when player attacks, Gudodama sphere shoots at target
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @EventHandler
            public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
                if (!(event.getDamager() instanceof Player player)) return;
                if (!gudodamaShiftState.getOrDefault(player.getUniqueId(), false) || !multiEntityCompanions.containsKey(player.getUniqueId())) {
                    return;
                }

                // Check if player has Gudodama equipped
                if (!hasGudodamaEquipped(player)) return;

                // Only trigger on left-click attack (not shift)
                if (player.isSneaking()) return;

                List<Entity> spheres = multiEntityCompanions.get(player.getUniqueId());
                if (spheres == null || spheres.isEmpty()) return;

                // Get target
                LivingEntity target = (event.getEntity() instanceof LivingEntity) ? (LivingEntity) event.getEntity() : null;
                if (target == null) return;

                // Shoot 7th sphere (index 6) at target
                if (spheres.size() > 6) {
                    Entity sphere = spheres.get(6);
                    if (sphere instanceof BlockDisplay blockDisplay && sphere.isValid()) {
                        shootSphereAtTarget(player, blockDisplay, target);
                    }
                }
            }
        }, plugin);
    }

    private boolean hasGudodamaEquipped(Player player) {
        var data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data == null) return false;
        return "gudodama".equals(data.getActive("companions"));
    }

    private void shootSphereAtTarget(Player player, BlockDisplay sphere, LivingEntity target) {
        try {
            // Play WITHER_SHOOT sound
            sphere.getWorld().playSound(sphere.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.2f);

            // Calculate direction to target
            Vector toTarget = target.getLocation().toVector().subtract(sphere.getLocation().toVector()).normalize();

            // Animate sphere flying to target (simplified - teleport in steps)
            Location start = sphere.getLocation().clone();
            Location end = target.getLocation().add(0, 1, 0);

            // Spawn trail particles
            for (double t = 0; t <= 1; t += 0.1) {
                Location trailLoc = start.clone().add(end.toVector().subtract(start.toVector()).multiply(t));
                getParticleManager().spawnParticle(Particle.SMOKE, trailLoc, 2, 0.1, 0.1, 0.1, 0.05);
                getParticleManager().spawnParticle(Particle.SQUID_INK, trailLoc, 2, 0.1, 0.1, 0.1, 0.05);
            }

            // Teleport sphere to target
            sphere.teleport(end);

            // Schedule return after 1 second (20 ticks)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (sphere.isValid() && player.isOnline()) {
                    // Return to orbit position
                    updateGudodamaAnimations(player.getUniqueId(), player);
                    getParticleManager().spawnParticle(Particle.SMOKE, sphere.getLocation(), 5, 0.2, 0.2, 0.2, 0.1);
                    getParticleManager().spawnParticle(Particle.SQUID_INK, sphere.getLocation(), 5, 0.2, 0.2, 0.2, 0.1);
                }
            }, 20L);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to shoot Gudodama sphere: " + e.getMessage());
        }
    }

    public void updateAll() {
        for (Map.Entry<UUID, Entity> entry : activePets.entrySet()) {
            UUID playerId = entry.getKey();
            Entity pet = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);

            if (player == null || !player.isOnline() || !pet.isValid()) {
                despawnPet(player);
                continue;
            }

            updatePetPosition(pet, player);
        }

        for (Map.Entry<UUID, Entity> entry : activeCompanions.entrySet()) {
            UUID playerId = entry.getKey();
            Entity companion = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);

            if (player == null || !player.isOnline() || !companion.isValid()) {
                despawnCompanion(playerId);
                continue;
            }

            // Update Gudodama animations if applicable
            if (multiEntityCompanions.containsKey(playerId)) {
                updateGudodamaAnimations(playerId, player);
            } else {
                Location targetLoc = companionLocations.get(playerId);
                if (targetLoc != null) {
                    companion.teleport(targetLoc);
                    // Also teleport multi-entity companions
                    List<Entity> multiEntities = multiEntityCompanions.get(playerId);
                    if (multiEntities != null) {
                        for (Entity e : multiEntities) {
                            if (e != null && e.isValid() && e != companion) {
                                e.teleport(targetLoc);
                            }
                        }
                    }
                }
            }
        }
    }

    private void updatePetPosition(Entity pet, Player player) {
        Location playerLoc = player.getLocation();
        Location petLoc = pet.getLocation();

        double distance = petLoc.distance(playerLoc);
        if (distance > 5) {
            Location target = playerLoc.clone().add(
                (Math.random() - 0.5) * 2,
                0,
                (Math.random() - 0.5) * 2
            );
            Vector direction = target.toVector().subtract(petLoc.toVector()).normalize().multiply(0.5);
            pet.setVelocity(pet.getVelocity().add(direction));
        }

        if (pet instanceof Mob mob) {
            mob.getPathfinder().moveTo(playerLoc, 1.0);
        }
    }

    public void cleanup() {
        for (Entity entity : activePets.values()) {
            if (entity.isValid()) entity.remove();
        }
        activePets.clear();

        for (Entity entity : activeCompanions.values()) {
            if (entity.isValid()) entity.remove();
        }
        activeCompanions.clear();

        // Clean up multi-entity companions
        for (List<Entity> entities : multiEntityCompanions.values()) {
            for (Entity entity : entities) {
                if (entity.isValid()) entity.remove();
            }
        }
        multiEntityCompanions.clear();

        companionLocations.clear();
        gudodamaShiftState.clear();
        gudodamaLastAttack.clear();
    }

    public boolean hasActivePet(UUID playerId) {
        Entity entity = activePets.get(playerId);
        return entity != null && entity.isValid();
    }

    public boolean hasActiveCompanion(UUID playerId) {
        Entity entity = activeCompanions.get(playerId);
        return entity != null && entity.isValid();
    }

    private ParticleManager getParticleManager() {
        return plugin.getParticleManager();
    }
}
