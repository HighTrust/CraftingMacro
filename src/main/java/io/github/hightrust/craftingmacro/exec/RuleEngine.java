package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.crafting.ScreenController;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Checks every running macro's {@link MacroRule}s once per tick. Timers keep counting across the
 * back-to-back runs of a hold/toggle loop, and reset when the macro is started fresh by its key.
 */
public final class RuleEngine {
	/** Minimum gap between two firings of a non-timer rule, on top of its pause. */
	private static final long RETRIGGER_MS = 5000;
	private static final double GROUND_RADIUS = 8;

	private final MacroExecutor executor;
	private final Consumer<String> notify;
	/** "macroId#ruleIndex" -> last time the rule fired (or its timer started). */
	private final Map<String, Long> lastFired = new HashMap<>();
	/** macroId -> last time a run of it was seen; used to forget stale timers. */
	private final Map<String, Long> lastSeen = new HashMap<>();

	public RuleEngine(MacroExecutor executor, Consumer<String> notify) {
		this.executor = executor;
		this.notify = notify;
	}

	/** Called when the user starts a macro by hand: its timers start from zero. */
	public void reset(String macroId) {
		lastFired.keySet().removeIf(k -> k.startsWith(macroId + "#"));
	}

	public void tick(long now) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;
		for (MacroRun run : new ArrayList<>(executor.activeRuns())) {
			Macro m = run.macro();
			lastSeen.put(m.id, now);
			if (m.rules == null || m.rules.isEmpty() || run.isFinished() || run.paused()) continue;
			for (int i = 0; i < m.rules.size(); i++) {
				MacroRule rule = m.rules.get(i);
				if (rule == null || !rule.enabled || rule.condition == null) continue;
				String key = m.id + "#" + i;
				Long last = lastFired.get(key);
				if (last == null) {
					lastFired.put(key, now);
					last = now - RETRIGGER_MS - rule.pauseMs;
					if (rule.condition == MacroRule.Condition.EVERY) continue;
				}
				if (!due(rule, player, mc, now, last)) continue;
				lastFired.put(key, now);
				fire(run, rule);
				break; // one rule per tick per macro
			}
		}
		// forget timers of macros that stopped looping a while ago
		lastSeen.entrySet().removeIf(e -> {
			if (now - e.getValue() < 10_000) return false;
			reset(e.getKey());
			return true;
		});
	}

	private static boolean due(MacroRule rule, LocalPlayer p, Minecraft mc, long now, long last) {
		return switch (rule.condition) {
			case EVERY -> now - last >= Math.max(1, rule.amount) * 1000L;
			case DURABILITY -> now - last >= RETRIGGER_MS + rule.pauseMs && durabilityLow(p, rule.amount);
			case INVENTORY_FULL -> now - last >= RETRIGGER_MS + rule.pauseMs && p.getInventory().getFreeSlot() < 0;
			case ITEMS_ON_GROUND -> now - last >= RETRIGGER_MS + rule.pauseMs && itemsOnGround(p, mc) >= Math.max(1, rule.amount);
		};
	}

	private static boolean durabilityLow(LocalPlayer p, int usesLeft) {
		ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
		if (held.isEmpty() || !held.isDamageableItem()) return false;
		return held.getMaxDamage() - held.getDamageValue() <= usesLeft;
	}

	private static int itemsOnGround(LocalPlayer p, Minecraft mc) {
		int n = 0;
		List<ItemEntity> items = mc.level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(GROUND_RADIUS));
		for (ItemEntity e : items) n += e.getItem().getCount();
		return n;
	}

	private void fire(MacroRun run, MacroRule rule) {
		Macro m = run.macro();
		String what = rule.condition.label() + (rule.condition.hasAmount() ? " " + rule.amount + " " + rule.condition.unit() : "");
		if (rule.then == MacroRule.Then.STOP) {
			run.interrupt(0, false, what);
			runCommand(rule);
			executor.cancel(m.id, "Rule: " + what);
			return;
		}
		run.interrupt(rule.pauseMs, rule.then == MacroRule.Then.RESTART, what);
		runCommand(rule);
		notify.accept(m.name + ": " + what + (rule.command == null || rule.command.isBlank() ? "" : " -> " + rule.command));
	}

	private static void runCommand(MacroRule rule) {
		if (rule.command != null && !rule.command.isBlank() && ScreenController.inWorld()) {
			ScreenController.sendCommand(rule.command);
		}
	}
}
