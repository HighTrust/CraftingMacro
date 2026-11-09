package io.github.hightrust.craftingmacro.world;

import io.github.hightrust.craftingmacro.exec.StepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Mines matching blocks around the player the same way a player does: turn to look at the block,
 * hold the attack key while the crosshair is on it, and let vanilla break it (server-validated).
 * Optionally walks straight toward blocks that are out of reach. Unreachable/stuck targets are
 * skipped. Ends when nothing matching is left (or the requested count is reached).
 */
public final class MineSession {
	private static final int BLOCK_TIMEOUT = 120;
	private static final int STUCK_TICKS = 30;

	private final String blocks;
	private final int maxBlocks;
	private final int radius;
	private final boolean walk;

	private BlockPos target;
	private int targetTicks;
	private int mined;
	private boolean attacking;
	private Vec3 lastPos;
	private int stuckTicks;
	/** Skipped positions -> tick they expire. */
	private final Map<BlockPos, Integer> skip = new HashMap<>();
	private int tick;

	public MineSession(String blocks, int maxBlocks, int radius, boolean walk) {
		this.blocks = blocks;
		this.maxBlocks = maxBlocks;
		this.radius = Math.max(1, radius);
		this.walk = walk;
	}

	public int mined() {
		return mined;
	}

	public StepResult tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) return StepResult.failed("Not in a world");
		boolean focused = PlayerActions.canUseMouseKeys();
		boolean background = io.github.hightrust.craftingmacro.MacroManager.get().settings().backgroundMining;
		if (!focused && !(background && mc.gui.screen() == null)) {
			stopKeys();
			if (mc.gui.screen() != null && background) {
				return StepResult.failed("Game paused when you tabbed out - press F3+P in-game to turn off pause on lost focus");
			}
			return StepResult.failed("Mining needs the game focused (or turn on 'Mine in background' in Settings)");
		}
		direct = !focused;
		tick++;
		skip.values().removeIf(exp -> exp < tick);

		// target finished?
		if (target != null && !matches(mc.level, target)) {
			if (attacking) mined++;
			target = null;
			stopKeys();
			if (maxBlocks > 0 && mined >= maxBlocks) return done();
		}
		if (target != null && ++targetTicks > BLOCK_TIMEOUT) {
			skip.put(target, tick + 200);
			target = null;
			stopKeys();
		}
		if (target == null) {
			target = findTarget(mc.level, p);
			targetTicks = 0;
			stuckTicks = 0;
			lastPos = p.position();
			if (target == null) {
				stopKeys();
				return done();
			}
		}

		Vec3 aim = aimPoint(mc.level, target);
		double reach = p.blockInteractionRange();
		boolean inReach = p.getEyePosition().distanceTo(aim) <= reach - 0.2;
		if (inReach) {
			PlayerActions.press(mc.options.keyUp, false);
			PlayerActions.press(mc.options.keyJump, false);
			float[] a = PlayerActions.anglesTo(aim);
			PlayerActions.turnTowards(a[0], a[1]);
			HitResult hit = mc.hitResult;
			boolean onTarget = hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK && bhr.getBlockPos().equals(target);
			if (direct) {
				// Unfocused: vanilla ignores the attack key, so do exactly what its mining loop does.
				PlayerActions.press(mc.options.keyAttack, false);
				if (onTarget) {
					BlockHitResult bhr = (BlockHitResult) hit;
					if (mc.gameMode.continueDestroyBlock(target, bhr.getDirection())) {
						var held = p.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
						p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, held.getAttackAnimation(), false);
						p.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
					}
					directDestroying = true;
				} else if (directDestroying) {
					mc.gameMode.stopDestroyBlock();
					directDestroying = false;
				}
			} else {
				PlayerActions.press(mc.options.keyAttack, onTarget);
			}
			attacking = attacking || onTarget;
		} else if (walk) {
			PlayerActions.press(mc.options.keyAttack, false);
			float[] a = PlayerActions.anglesTo(aim);
			PlayerActions.turnTowards(a[0], Math.min(30f, Math.max(-10f, a[1])));
			PlayerActions.press(mc.options.keyUp, true);
			PlayerActions.press(mc.options.keyJump, p.horizontalCollision);
			if (p.position().distanceTo(lastPos) < 0.03) {
				if (++stuckTicks > STUCK_TICKS) {
					skip.put(target, tick + 400);
					target = null;
					stopKeys();
				}
			} else {
				stuckTicks = 0;
			}
			lastPos = p.position();
		} else {
			skip.put(target, tick + 100);
			target = null;
		}
		return StepResult.running();
	}

	private StepResult done() {
		stopKeys();
		return StepResult.done(mined > 0 ? "Mined " + mined + " block" + (mined == 1 ? "" : "s") : "");
	}

	public void cancel() {
		stopKeys();
	}

	private boolean direct;
	private boolean directDestroying;

	private void stopKeys() {
		attacking = false;
		Minecraft mc = Minecraft.getInstance();
		if (directDestroying && mc.gameMode != null) {
			mc.gameMode.stopDestroyBlock();
			directDestroying = false;
		}
		PlayerActions.press(mc.options.keyAttack, false);
		PlayerActions.press(mc.options.keyUp, false);
		PlayerActions.press(mc.options.keyJump, false);
	}

	private boolean matches(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return false;
		return PlayerActions.matchesAny(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), blocks);
	}

	/** Centre of the block's outline (so thin blocks like snow layers are aimed at correctly). */
	private static Vec3 aimPoint(Level level, BlockPos pos) {
		VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
		if (shape.isEmpty()) return Vec3.atCenterOf(pos);
		return shape.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
	}

	/** Nearest visible block in reach; otherwise (walk mode) the nearest one within the radius. */
	private BlockPos findTarget(Level level, LocalPlayer p) {
		Vec3 eye = p.getEyePosition();
		double reach = p.blockInteractionRange() - 0.2;
		BlockPos origin = p.blockPosition();
		BlockPos bestReach = null;
		double bestReachDist = Double.MAX_VALUE;
		BlockPos bestFar = null;
		double bestFarDist = Double.MAX_VALUE;
		int vr = Math.min(radius, 6);
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -vr, -radius), origin.offset(radius, vr, radius))) {
			if (skip.containsKey(pos) || !matches(level, pos)) continue;
			Vec3 aim = aimPoint(level, pos);
			double d = eye.distanceTo(aim);
			if (d <= reach) {
				if (d < bestReachDist && visible(level, p, eye, aim, pos)) {
					bestReach = pos.immutable();
					bestReachDist = d;
				}
			} else if (walk && d < bestFarDist) {
				bestFar = pos.immutable();
				bestFarDist = d;
			}
		}
		return bestReach != null ? bestReach : bestFar;
	}

	private static boolean visible(Level level, LocalPlayer p, Vec3 eye, Vec3 aim, BlockPos pos) {
		BlockHitResult hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
	}
}
