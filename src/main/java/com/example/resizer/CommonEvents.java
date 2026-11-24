package com.example.resizer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ResizerMod.MOD_ID)
public class CommonEvents {
    private static final String KEY = "resizer_applied_scale";
    private static boolean breaking = false;

    /** ПКМ прибором по существу (в том числе по жителям, у которых иначе открылась бы торговля). */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!e.getItemStack().is(ResizerMod.RESIZER.get())) return;
        if (!(e.getTarget() instanceof LivingEntity target)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!e.getLevel().isClientSide) {
            ResizeHelper.useOn(e.getEntity(), target);
        }
    }

    /** Следим за изменением размера и пересчитываем хитбокс (и на сервере, и на клиенте). */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        LivingEntity en = e.getEntity();
        double f = ResizeHelper.scale(en);
        CompoundTag data = en.getPersistentData();
        double prev = data.contains(KEY) ? data.getDouble(KEY) : 1.0;
        if (Math.abs(prev - f) > 1e-6) {
            data.putDouble(KEY, f);
            en.refreshDimensions();
        }
    }

    /** После загрузки из сохранения заставляем пересчитать хитбокс. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        e.getEntity().getPersistentData().remove(KEY);
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        Entity en = e.getEntity();
        CompoundTag data = en.getPersistentData();
        if (!data.contains(KEY)) return;
        float f = (float) data.getDouble(KEY);
        if (f == 1.0f) return;
        e.setNewSize(e.getNewSize().scale(f));
        e.setNewEyeHeight(e.getNewEyeHeight() * f);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent e) {
        LivingEntity en = e.getEntity();
        double f = ResizeHelper.scale(en);
        if (f == 1.0) return;
        Vec3 m = en.getDeltaMovement();
        en.setDeltaMovement(m.x, m.y * Math.sqrt(f), m.z);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        double f = ResizeHelper.scale(e.getEntity());
        if (f != 1.0) e.setDistance((float) (e.getDistance() / f));
    }

    /** Размер x2 и больше: ломаем куб блоков вокруг (x2 = 3x3x3, x3 = 5x5x5, x4 = 7x7x7). С Shift ломается один блок. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (breaking) return;
        Player p = e.getPlayer();
        if (!(p instanceof ServerPlayer sp) || p.isShiftKeyDown()) return;
        int r = (int) Math.floor(ResizeHelper.scale(p) + 1e-6) - 1;
        if (r <= 0) return;

        ServerLevel level = sp.serverLevel();
        BlockPos origin = e.getPos();
        breaking = true;
        try {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos pos = origin.offset(dx, dy, dz);
                        BlockState s = level.getBlockState(pos);
                        if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                        if (s.getDestroySpeed(level, pos) < 0) continue;
                        if (s.requiresCorrectToolForDrops()
                                && !sp.getMainHandItem().isCorrectToolForDrops(s)) continue;
                        sp.gameMode.destroyBlock(pos);
                    }
                }
            }
        } finally {
            breaking = false;
        }
    }
}
