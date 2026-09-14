package dev.slowy.core.guild;

import dev.slowy.core.utils.ColorUtils;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces Anti Friendly-Fire between members of the same guild.
 * Covers melee, long-range (projectiles), and magic/potions (splash & lingering harmful effects).
 */
@NullMarked
public final class GuildCombatListener implements Listener {

    private final GuildManager guildManager;
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();

    public GuildCombatListener(GuildManager guildManager) {
        this.guildManager = Objects.requireNonNull(guildManager, "guildManager cannot be null");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player damager = resolveDamager(event.getDamager());
        if (damager == null || damager.equals(victim)) {
            return;
        }

        if (guildManager.isSameGuild(damager.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            warnAttacker(damager);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) {
            return;
        }

        Guild throwerGuild = guildManager.getGuildByPlayer(thrower.getUniqueId());
        if (throwerGuild == null) {
            return;
        }

        boolean hasHarmful = false;
        for (PotionEffect effect : event.getPotion().getEffects()) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                hasHarmful = true;
                break;
            }
        }

        if (!hasHarmful) {
            return;
        }

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim && !victim.equals(thrower)) {
                if (throwerGuild.isMember(victim.getUniqueId())) {
                    event.setIntensity(victim, 0.0);
                    warnAttacker(thrower);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player thrower)) {
            return;
        }

        Guild throwerGuild = guildManager.getGuildByPlayer(thrower.getUniqueId());
        if (throwerGuild == null) {
            return;
        }

        boolean hasHarmful = false;
        for (PotionEffect effect : cloud.getCustomEffects()) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                hasHarmful = true;
                break;
            }
        }
        if (cloud.getBasePotionType() != null) {
            for (PotionEffect effect : cloud.getBasePotionType().getPotionEffects()) {
                if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                    hasHarmful = true;
                    break;
                }
            }
        }

        if (hasHarmful) {
            event.getAffectedEntities().removeIf(entity ->
                    entity instanceof Player victim &&
                    !victim.equals(thrower) &&
                    throwerGuild.isMember(victim.getUniqueId())
            );
        }
    }

    private @Nullable Player resolveDamager(Entity damagerEntity) {
        if (damagerEntity instanceof Player p) {
            return p;
        }
        if (damagerEntity instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        if (damagerEntity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) {
            return p;
        }
        if (damagerEntity instanceof ThrownPotion potion && potion.getShooter() instanceof Player p) {
            return p;
        }
        if (damagerEntity instanceof Firework firework && firework.getShooter() instanceof Player p) {
            return p;
        }
        if (damagerEntity instanceof LightningStrike lightning && lightning.getCausingEntity() instanceof Player p) {
            return p;
        }
        return null;
    }

    private void warnAttacker(Player attacker) {
        long now = System.currentTimeMillis();
        Long prev = lastWarn.get(attacker.getUniqueId());
        if (prev == null || now - prev > 1500L) {
            lastWarn.put(attacker.getUniqueId(), now);
            attacker.sendActionBar(ColorUtils.parse("<#FF0055>✖ Cannot attack your fellow guild member!</#FF0055>"));
        }
    }
}
