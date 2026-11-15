package com.borislav.freeflow.test;

import com.borislav.freeflow.combat.CombatAction;
import com.borislav.freeflow.combat.CombatMode;
import com.borislav.freeflow.combat.CombatModeTracker;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.CombatStateManager;
import com.borislav.freeflow.network.CombatInputPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Automated in-game tests, run headless with {@code ./gradlew runGametest}.
 * Only active when the server is started with -Dfabric-api.gametest.
 *
 * The mock player never moves on its own (movement is client-side), so lunges are
 * simulated by teleporting the player where the client would have dashed.
 * Player stands at relative (2.5, 1, 2.5) facing +Z (yaw 0).
 */
public class FreeflowGameTests implements FabricGameTest {

	private static final float FACING_PLUS_Z = 0.0f;

	private static ServerPlayer player(GameTestHelper h, CombatMode mode) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		Vec3 pos = h.absoluteVec(new Vec3(2.5, 1, 2.5));
		p.moveTo(pos.x, pos.y, pos.z, FACING_PLUS_Z, 0.0f);
		p.setNoGravity(true);
		p.setGameMode(GameType.SURVIVAL); // mock players may start in creative (immune to damage)
		CombatState state = CombatStateManager.get(p);
		state.mode = mode;
		CombatModeTracker.tick(p, state);
		return p;
	}

	private static Zombie zombie(GameTestHelper h, double x, double z) {
		Zombie zombie = h.spawn(EntityType.ZOMBIE, (float) x, 1.0f, (float) z);
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		// The test world is in daylight: a helmet stops sun burning, which would otherwise damage it.
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		return zombie;
	}

	private static void strike(ServerPlayer p, float aimYaw, float forward, float left) {
		CombatStateManager.handleInput(p, new CombatInputPayload(CombatAction.STRIKE, forward, left, aimYaw));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strikeHitsAdjacentZombie(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 4.0);
		float startHp = z.getHealth();

		strike(p, FACING_PLUS_Z, 0, 0);
		h.succeedWhen(() -> h.assertTrue(z.getHealth() < startHp, "zombie should have taken damage"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strikePicksEnemyInHeldDirection(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie ahead = zombie(h, 2.5, 6.5);  // 4 blocks in front (+Z)
		Zombie behind = zombie(h, 2.5, 0.5); // 2 blocks behind (-Z)
		CombatState state = CombatStateManager.get(p);

		strike(p, FACING_PLUS_Z, 1.0f, 0.0f); // push forward: should ignore the closer one behind
		h.assertTrue(state.strikeTarget == ahead, "forward strike should pick the zombie ahead");

		state.clearMoves();
		state.nextStrikeTick = 0;
		strike(p, FACING_PLUS_Z, -1.0f, 0.0f); // push backward
		h.assertTrue(state.strikeTarget == behind, "backward strike should pick the zombie behind");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lungeStrikeLandsWhenPlayerArrives(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 7.5); // 5 blocks away: needs a lunge
		float startHp = z.getHealth();
		CombatState state = CombatStateManager.get(p);

		strike(p, FACING_PLUS_Z, 1.0f, 0.0f);
		h.assertTrue(state.strikeTarget == z, "should target the distant zombie");
		h.assertTrue(state.strikeHitTick > h.getLevel().getGameTime() + 1, "a 5-block strike should take several ticks");

		// Simulate the client's dash: end up right in front of the zombie.
		Vec3 arrived = h.absoluteVec(new Vec3(2.5, 1, 6.4));
		p.moveTo(arrived.x, arrived.y, arrived.z, FACING_PLUS_Z, 0.0f);
		h.succeedWhen(() -> h.assertTrue(z.getHealth() < startHp, "zombie should be hit after the lunge"));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void lungeStrikeMissesIfPlayerNeverArrives(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 7.5); // 5 blocks away (test area is 8x8x8), player stays put
		float startHp = z.getHealth();

		strike(p, FACING_PLUS_Z, 1.0f, 0.0f);
		CombatState state = CombatStateManager.get(p);
		h.assertTrue(state.strikeTarget == z, "should target the zombie 5 blocks ahead");
		h.runAfterDelay(15, () -> {
			h.assertTrue(z.getHealth() == startHp, String.format(
					"server must not apply a hit from 5 blocks away (hp %.1f -> %.1f, player %s, zombie %s, fire %s)",
					startHp, z.getHealth(), p.position(), z.position(), z.isOnFire()));
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void noStrikeWhenFreeflowOff(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.OFF);
		Zombie z = zombie(h, 2.5, 4.0);
		float startHp = z.getHealth();

		strike(p, FACING_PLUS_Z, 0, 0);
		h.runAfterDelay(10, () -> {
			h.assertTrue(z.getHealth() == startHp, "strike must be ignored with combat mode OFF");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void autoModeEntersCombatWhenTargeted(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.AUTO);
		Zombie z = zombie(h, 2.5, 6.5);
		z.setTarget(p);
		h.succeedWhen(() -> h.assertTrue(CombatStateManager.get(p).inCombat, "AUTO mode should enter combat"));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void playerInvulnerableDuringLunge(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 7.5);

		// Query our damage hook directly: mock players have creative-like damage rules of their own.
		strike(p, FACING_PLUS_Z, 1.0f, 0.0f);
		h.assertTrue(!ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, p.damageSources().mobAttack(z), 2.0f),
				"player should ignore damage while lunging");
		h.runAfterDelay(15, () -> {
			h.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, p.damageSources().mobAttack(z), 2.0f),
					"player should be hurtable again after the lunge");
			h.succeed();
		});
	}

	// --- Milestone 5: combo & rhythm ---

	@GameTest(template = EMPTY_STRUCTURE)
	public void comboCountsLandedHits(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		zombie(h, 2.5, 4.0);
		CombatState state = CombatStateManager.get(p);

		strike(p, FACING_PLUS_Z, 0, 0);
		h.runAfterDelay(6, () -> strike(p, FACING_PLUS_Z, 0, 0));
		h.succeedWhen(() -> h.assertTrue(state.combo == 2, "combo should be 2 after two hits, was " + state.combo));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void comboBreaksAfterTimeout(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		zombie(h, 2.5, 4.0);
		CombatState state = CombatStateManager.get(p);

		strike(p, FACING_PLUS_Z, 0, 0);
		h.runAfterDelay(5, () -> h.assertTrue(state.combo == 1, "combo should be 1 after a hit"));
		h.runAfterDelay(45, () -> {
			h.assertTrue(state.combo == 0, "combo should time out after 1.5s without hits");
			h.assertTrue(state.maxCombo == 1, "max combo should remember the best combo of the fight");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void whiffBreaksCombo(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		CombatState state = CombatStateManager.get(p);
		state.combo = 5;
		state.lastComboTick = h.getLevel().getGameTime();

		strike(p, FACING_PLUS_Z, 0, 0); // nothing to hit
		h.assertTrue(state.combo == 0, "striking at air should break the combo");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onBeatPressIsBuffered(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		zombie(h, 2.5, 4.0); // adjacent: hit lands next tick, so a press now is on-beat
		CombatState state = CombatStateManager.get(p);

		strike(p, FACING_PLUS_Z, 0, 0);
		strike(p, FACING_PLUS_Z, 0, 0);
		h.assertTrue(state.bufferedStrike != null, "on-beat press should be buffered");
		h.assertTrue(state.staggerUntil <= h.getLevel().getGameTime(), "on-beat press must not stagger");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mashingCausesStagger(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Vec3 start = h.absoluteVec(new Vec3(2.5, 1, 0.5));
		p.moveTo(start.x, start.y, start.z, FACING_PLUS_Z, 0.0f);
		zombie(h, 2.5, 7.5); // 7 blocks: a 4-tick lunge, so presses right away are off-beat
		CombatState state = CombatStateManager.get(p);

		strike(p, FACING_PLUS_Z, 1.0f, 0);
		h.assertTrue(state.strikeHitTick - h.getLevel().getGameTime() > 3, "lunge should be long enough to be off-beat");
		strike(p, FACING_PLUS_Z, 1.0f, 0);
		strike(p, FACING_PLUS_Z, 1.0f, 0);
		h.assertTrue(state.staggerUntil > h.getLevel().getGameTime(), "mashing should stagger the player");
		h.assertTrue(state.bufferedStrike == null, "stagger should drop the buffered press");

		int acked = state.acceptedInputs;
		strike(p, FACING_PLUS_Z, 1.0f, 0);
		h.assertTrue(state.acceptedInputs == acked, "inputs during a stagger should be ignored");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void damageScalesWithCombo(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		CombatState state = CombatStateManager.get(p);
		Zombie first = zombie(h, 2.5, 4.0);
		float firstHp = first.getHealth();

		strike(p, FACING_PLUS_Z, 0, 0); // combo 0 hit
		h.runAfterDelay(4, () -> {
			float lossAtZero = firstHp - first.getHealth();
			h.assertTrue(lossAtZero > 0, "first hit should land");
			first.discard();

			Zombie second = zombie(h, 2.5, 4.0);
			float secondHp = second.getHealth();
			state.combo = 10;
			state.lastComboTick = h.getLevel().getGameTime();
			state.nextStrikeTick = 0;
			strike(p, FACING_PLUS_Z, 0, 0); // combo 10 hit: +50% damage
			h.runAfterDelay(4, () -> {
				float lossAtTen = secondHp - second.getHealth();
				h.assertTrue(lossAtTen > lossAtZero * 1.3f,
						String.format("combo x10 should hit harder (%.2f vs %.2f)", lossAtTen, lossAtZero));
				h.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takingDamageBreaksCombo(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		CombatState state = CombatStateManager.get(p);
		state.combo = 5;
		state.lastComboTick = h.getLevel().getGameTime();

		ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(p, p.damageSources().generic(), 2.0f, 2.0f, false);
		h.assertTrue(state.combo == 0, "getting hit should break the combo");
		h.succeed();
	}
}
