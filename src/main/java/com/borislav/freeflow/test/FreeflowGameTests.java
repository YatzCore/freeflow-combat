package com.borislav.freeflow.test;

import com.borislav.freeflow.ai.CombatDirector;
import com.borislav.freeflow.ai.EnemyRole;
import com.borislav.freeflow.ai.EnemySlot;
import com.borislav.freeflow.network.TelegraphPayload;
import com.borislav.freeflow.ai.EnemyStatus;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
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

	// --- Milestone 6: combat director & counter ---
	// Mock players count as creative, so mobs won't target them on their own:
	// these tests drive the director directly (the AI hook calls the same methods).

	@GameTest(template = EMPTY_STRUCTURE)
	public void directorOnlyAppliesInFreeflow(GameTestHelper h) {
		ServerPlayer on = player(h, CombatMode.FORCED_ON);
		h.assertTrue(CombatDirector.isDirected(on), "freeflow player should be directed");
		CombatStateManager.get(on).mode = CombatMode.OFF;
		CombatModeTracker.tick(on, CombatStateManager.get(on));
		h.assertTrue(!CombatDirector.isDirected(on), "with combat OFF mobs should fight vanilla-style");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void telegraphedAttackLandsAfterWarning(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 3.5);
		CombatState state = CombatStateManager.get(p);

		h.assertTrue(CombatDirector.requestAttack(p, z), "first attacker should get a turn");
		EnemySlot slot = state.enemies.get(z.getId());
		h.assertTrue(slot.isTelegraphing(), "attack should be telegraphed first");
		h.assertTrue(!z.swinging, "zombie must not hit before the warning ends");
		h.succeedWhen(() -> {
			h.assertTrue(!slot.isTelegraphing(), "attack should resolve after the warning");
			h.assertTrue(z.swinging, "zombie should swing when the attack lands");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void directorLimitsSimultaneousAttackers(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie a = zombie(h, 2.5, 3.5);
		Zombie b = zombie(h, 3.5, 2.5);
		Zombie c = zombie(h, 1.5, 2.5);
		Zombie d = zombie(h, 3.5, 3.5);
		Zombie e = zombie(h, 1.5, 3.5);
		CombatState state = CombatStateManager.get(p);

		h.assertTrue(CombatDirector.requestAttack(p, a), "A should get a turn");
		h.assertTrue(!CombatDirector.requestAttack(p, b), "B must wait for the gap between attacks");

		// Skip the start gap to check the cap on its own.
		for (Zombie z : new Zombie[] {b, c, d}) {
			state.lastTelegraphTick = -100;
			h.assertTrue(CombatDirector.requestAttack(p, z), "up to 4 enemies may attack at once");
		}
		state.lastTelegraphTick = -100;
		h.assertTrue(CombatDirector.activeAttacks(state) == 4, "four attacks should be in progress");
		h.assertTrue(!CombatDirector.requestAttack(p, e), "the 5th enemy must wait: max 4 attackers at once");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void counterCancelsAttackAndStuns(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 3.5);
		CombatState state = CombatStateManager.get(p);
		float hp = z.getHealth();

		CombatDirector.requestAttack(p, z);
		CombatStateManager.handleInput(p, new CombatInputPayload(CombatAction.COUNTER, 0, 0, FACING_PLUS_Z));
		EnemySlot slot = state.enemies.get(z.getId());
		long now = h.getLevel().getGameTime();
		h.assertTrue(!slot.isTelegraphing(), "counter should cancel the attack");
		h.assertTrue(slot.isStunned(now), "countered enemy should be stunned");
		h.assertTrue(z.getHealth() < hp, "counter should damage the enemy");
		h.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "countered enemy should be slowed");
		h.assertTrue(state.combo == 1, "counter should add to the combo");
		h.assertTrue(!CombatDirector.requestAttack(p, z), "stunned enemy can't attack");
		h.runAfterDelay(15, () -> {
			h.assertTrue(!z.swinging, "countered attack must never land");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void eachCounterPressHandlesOneAttacker(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie a = zombie(h, 2.5, 3.5);
		Zombie b = zombie(h, 3.5, 2.5);
		CombatState state = CombatStateManager.get(p);

		CombatDirector.requestAttack(p, a);
		h.runAfterDelay(4, () -> {
			h.assertTrue(CombatDirector.requestAttack(p, b), "B should start its attack");
			counter(p);
			h.assertTrue(CombatDirector.activeAttacks(state) == 1, "one press should counter only one attacker");
			h.assertTrue(!state.enemies.get(a.getId()).isTelegraphing(), "the soonest attack (A) is countered first");
			counter(p);
			h.assertTrue(CombatDirector.activeAttacks(state) == 0, "a second press should counter the second attacker");
			h.assertTrue(state.combo == 2, "each countered enemy adds a combo hit, combo was " + state.combo);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void missedCounterLetsAttackLand(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie a = zombie(h, 2.5, 3.5);
		Zombie b = zombie(h, 3.5, 2.5);
		CombatState state = CombatStateManager.get(p);

		CombatDirector.requestAttack(p, a);
		h.runAfterDelay(4, () -> {
			CombatDirector.requestAttack(p, b);
			counter(p); // only one press for two warnings
			h.succeedWhen(() -> {
				h.assertTrue(CombatDirector.activeAttacks(state) == 0, "B's attack should resolve");
				h.assertTrue(b.swinging, "the uncountered attacker should hit");
				h.assertTrue(!a.swinging, "the countered attacker should not hit");
			});
		});
	}

	// --- Milestone 7: evade & vault ---

	private static void evade(ServerPlayer p, float forward, float left) {
		CombatStateManager.handleInput(p, new CombatInputPayload(CombatAction.EVADE, forward, left, FACING_PLUS_Z));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void evadeGivesInvulnerability(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 6.5);
		evade(p, 0, 1.0f); // sideways, nothing to vault
		h.assertTrue(!ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, p.damageSources().mobAttack(z), 2.0f),
				"player should be invulnerable while evading");
		h.runAfterDelay(15, () -> {
			h.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, p.damageSources().mobAttack(z), 2.0f),
					"invulnerability should wear off after the evade");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void evadeCancelsStrikeAndKeepsCombo(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 7.5);
		CombatState state = CombatStateManager.get(p);
		state.combo = 5;
		state.lastComboTick = h.getLevel().getGameTime();
		float hp = z.getHealth();

		strike(p, FACING_PLUS_Z, 1.0f, 0); // lunge toward the zombie...
		evade(p, -1.0f, 0);                 // ...then dodge back out of it
		h.assertTrue(state.strikeHitTick < 0, "evade should cancel the strike in flight");
		h.assertTrue(state.combo == 5, "evading keeps the combo");
		h.runAfterDelay(10, () -> {
			h.assertTrue(z.getHealth() == hp, "a cancelled strike must not land");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void evadeHasCooldown(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		CombatState state = CombatStateManager.get(p);
		evade(p, 0, 1.0f);
		int acked = state.acceptedInputs;
		evade(p, 0, -1.0f);
		h.assertTrue(state.acceptedInputs == acked, "a second evade during the cooldown should be ignored");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void evadeTowardEnemyVaults(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 4.5); // 2 blocks ahead
		CombatState state = CombatStateManager.get(p);

		evade(p, 1.0f, 0); // toward it
		h.assertTrue(state.lastVaultTarget == z, "evading toward a close enemy should vault over it");

		state.evadeCooldownUntil = 0;
		evade(p, -1.0f, 0); // away from it
		h.assertTrue(state.lastVaultTarget == null, "evading away should be a plain evade");
		h.succeed();
	}

	// --- Milestone 8: stun, beatdown, takedowns ---

	private static void act(ServerPlayer p, CombatAction action) {
		CombatStateManager.handleInput(p, new CombatInputPayload(action, 0, 0, FACING_PLUS_Z));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stunCancelsAttackAndStuns(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 3.5);
		CombatState state = CombatStateManager.get(p);

		CombatDirector.requestAttack(p, z);
		act(p, CombatAction.STUN);
		EnemySlot slot = state.enemies.get(z.getId());
		long now = h.getLevel().getGameTime();
		h.assertTrue(!slot.isTelegraphing(), "stun should cancel the enemy's attack");
		h.assertTrue(slot.isStunned(now), "enemy should be stunned");
		h.assertTrue(!CombatDirector.requestAttack(p, z), "stunned enemy can't attack");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void beatdownAllowsMashing(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		zombie(h, 2.5, 3.5);
		CombatState state = CombatStateManager.get(p);

		act(p, CombatAction.STUN);
		for (int i = 0; i < 4; i++) {
			act(p, CombatAction.STRIKE);
		}
		h.assertTrue(state.staggerUntil <= h.getLevel().getGameTime(), "mashing a stunned enemy must not stagger");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void beatdownFinisherKnocksDown(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 3.5);
		CombatState state = CombatStateManager.get(p);

		act(p, CombatAction.STUN);
		h.onEachTick(() -> act(p, CombatAction.STRIKE)); // mash
		h.succeedWhen(() -> {
			EnemySlot slot = state.enemies.get(z.getId());
			h.assertTrue(slot != null && slot.isKnockedDown(h.getLevel().getGameTime()),
					"the beatdown finisher should knock the enemy down");
			h.assertTrue(z.isAlive(), "a beatdown alone shouldn't kill a fresh zombie");
		});
	}

	/** Full chain as a player does it: stun, mash the beatdown, then V on the knocked-down enemy. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void beatdownThenGroundTakedownChain(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Vec3 start = h.absoluteVec(new Vec3(2.5, 1, 1.5));
		p.moveTo(start.x, start.y, start.z, FACING_PLUS_Z, 0.0f);
		Zombie z = h.spawn(EntityType.ZOMBIE, 2.5f, 1.0f, 2.5f); // real physics: knockback moves it
		// AI stays on: a no-AI mob ignores knockback, which hid the original bug.
		z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		CombatState state = CombatStateManager.get(p);

		act(p, CombatAction.STUN);
		boolean[] knockedDown = {false};
		h.onEachTick(() -> {
			EnemySlot slot = state.enemies.get(z.getId());
			long now = h.getLevel().getGameTime();
			if (!knockedDown[0] && slot != null && slot.isKnockedDown(now)) {
				knockedDown[0] = true;
				// Give the knockback a moment to play out, then take it down.
				h.runAfterDelay(10, () -> {
					act(p, CombatAction.GROUND_TAKEDOWN);
					h.assertTrue(state.takedownTarget == z, String.format(
							"V should start a ground takedown on the knocked-down zombie (distance %.2f)", z.distanceTo(p)));
				});
			} else if (!knockedDown[0]) {
				act(p, CombatAction.STRIKE); // mash the beatdown
			}
		});
		h.succeedWhen(() -> {
			EnemySlot slot = state.enemies.get(z.getId());
			long now = h.getLevel().getGameTime();
			h.assertTrue(!z.isAlive(), String.format(
					"the chain should end with the zombie taken down (knockedDown=%s, hp=%.1f, dist=%.2f, "
							+ "stunned=%s, beatdownHits=%s, takedown=%s, lastAction=%s, acked=%d, inCombat=%s)",
					knockedDown[0], z.getHealth(), z.distanceTo(p),
					slot != null && now < slot.stunnedUntil, slot != null ? slot.beatdownHits : -1,
					state.takedownTarget != null, state.lastAction, state.acceptedInputs, state.inCombat));
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void groundTakedownFinishesKnockedDownEnemy(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 3.5);
		CombatState state = CombatStateManager.get(p);

		EnemyStatus.knockDown(p, state, z, 60);
		act(p, CombatAction.GROUND_TAKEDOWN);
		h.assertTrue(state.lockedUntil > h.getLevel().getGameTime(), "player is locked during a ground takedown");
		h.succeedWhen(() -> h.assertTrue(!z.isAlive(), "ground takedown should knock the enemy out"));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void groundTakedownInterruptedByDamage(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 3.5);
		CombatState state = CombatStateManager.get(p);

		EnemyStatus.knockDown(p, state, z, 60);
		act(p, CombatAction.GROUND_TAKEDOWN);
		ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(p, p.damageSources().generic(), 2.0f, 2.0f, false);
		h.assertTrue(state.takedownTarget == null, "getting hit should interrupt the takedown");
		h.runAfterDelay(30, () -> {
			h.assertTrue(z.isAlive(), "an interrupted takedown must not finish the enemy");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void groundTakedownNeedsKnockedDownEnemy(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		zombie(h, 2.5, 3.5);
		CombatState state = CombatStateManager.get(p);
		int acked = state.acceptedInputs;
		act(p, CombatAction.GROUND_TAKEDOWN);
		h.assertTrue(state.acceptedInputs == acked, "no takedown on a standing enemy");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void specialTakedownSpendsComboAndKnocksOut(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 4.0);
		CombatState state = CombatStateManager.get(p);

		state.combo = 3;
		state.lastComboTick = h.getLevel().getGameTime();
		int acked = state.acceptedInputs;
		act(p, CombatAction.SPECIAL_TAKEDOWN);
		h.assertTrue(state.acceptedInputs == acked, "special takedown needs x8 combo");

		state.combo = 9;
		act(p, CombatAction.SPECIAL_TAKEDOWN);
		h.assertTrue(state.combo == 1, "special takedown should spend 8 combo, combo is " + state.combo);
		h.succeedWhen(() -> h.assertTrue(!z.isAlive(), "special takedown should knock the enemy out"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesSurviveTakedowns(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		IronGolem golem = h.spawn(EntityType.IRON_GOLEM, 2.5f, 1.0f, 4.5f);
		golem.setNoAi(true);
		h.assertTrue(EnemyStatus.isBoss(golem), "an iron golem (100 hp) counts as a boss");
		EnemyStatus.knockOut(p, golem);
		h.assertTrue(golem.isAlive(), "bosses only take heavy damage from takedowns");
		h.succeed();
	}

	// --- Milestone 9: enemy roles ---

	private static <T extends Mob> T still(T mob, float yaw) {
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no sun burning
		return mob;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rolesComeFromTagsAndGear(GameTestHelper h) {
		Zombie plain = still(h.spawn(EntityType.ZOMBIE, 1.5f, 1, 1.5f), 0);
		Zombie shielded = still(h.spawn(EntityType.ZOMBIE, 3.5f, 1, 1.5f), 0);
		shielded.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		Zombie withSword = still(h.spawn(EntityType.ZOMBIE, 5.5f, 1, 1.5f), 0);
		withSword.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Vindicator vindicator = still(h.spawn(EntityType.VINDICATOR, 1.5f, 1, 4.5f), 0);
		Skeleton skeleton = still(h.spawn(EntityType.SKELETON, 3.5f, 1, 4.5f), 0);
		IronGolem golem = still(h.spawn(EntityType.IRON_GOLEM, 5.5f, 1, 5.5f), 0);
		// Spawning a pillager also loads RangedCrossbowAttackGoal, so its mixin is verified here.
		Pillager pillager = still(h.spawn(EntityType.PILLAGER, 1.5f, 1, 6.5f), 0);
		h.assertTrue(EnemyRole.of(pillager) == EnemyRole.RANGED, "pillager is RANGED (tag)");

		h.assertTrue(EnemyRole.of(plain) == EnemyRole.NORMAL, "plain zombie is NORMAL");
		h.assertTrue(EnemyRole.of(shielded) == EnemyRole.SHIELDED, "zombie with a shield is SHIELDED");
		h.assertTrue(EnemyRole.of(withSword) == EnemyRole.ARMED, "zombie with a sword is ARMED");
		h.assertTrue(EnemyRole.of(vindicator) == EnemyRole.ARMED, "vindicator is ARMED (tag)");
		h.assertTrue(EnemyRole.of(skeleton) == EnemyRole.RANGED, "skeleton is RANGED (tag)");
		h.assertTrue(EnemyRole.of(golem) == EnemyRole.BOSS, "iron golem is a BOSS (tag)");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void shieldBlocksStrikeFromTheFront(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = still(h.spawn(EntityType.ZOMBIE, 2.5f, 1, 4.0f), 180.0f); // facing the player (-Z)
		z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		CombatState state = CombatStateManager.get(p);
		float hp = z.getHealth();

		strike(p, FACING_PLUS_Z, 0, 0);
		h.runAfterDelay(5, () -> {
			h.assertTrue(z.getHealth() == hp, "a frontal strike should bounce off the shield");
			h.assertTrue(state.staggerUntil > state.lastComboTick, "bouncing off a shield makes the player recoil");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void shieldDoesNotBlockFromBehind(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = still(h.spawn(EntityType.ZOMBIE, 2.5f, 1, 4.0f), 0.0f); // facing away (+Z)
		z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		float hp = z.getHealth();

		strike(p, FACING_PLUS_Z, 0, 0);
		h.succeedWhen(() -> h.assertTrue(z.getHealth() < hp, "a strike from behind should land"));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void stunnedShieldCanBeBeatenDown(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = still(h.spawn(EntityType.ZOMBIE, 2.5f, 1, 4.0f), 180.0f);
		z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		float hp = z.getHealth();

		act(p, CombatAction.STUN);
		strike(p, FACING_PLUS_Z, 0, 0);
		h.succeedWhen(() -> h.assertTrue(z.getHealth() < hp, "a stunned shield enemy can be hit from the front"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void armedAttackCantBeCountered(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Vindicator v = still(h.spawn(EntityType.VINDICATOR, 2.5f, 1, 3.5f), 180.0f);
		CombatState state = CombatStateManager.get(p);

		h.assertTrue(CombatDirector.requestAttack(p, v), "vindicator should get a turn");
		EnemySlot slot = state.enemies.get(v.getId());
		h.assertTrue(slot.kind == TelegraphPayload.ARMED, "armed attack should have the red telegraph");
		counter(p);
		h.assertTrue(slot.isTelegraphing(), "an armed attack can't be countered");
		h.assertTrue(state.counterCooldownUntil > h.getLevel().getGameTime(), "failed counter locks out");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void rangedShotIsTelegraphedAndFires(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Skeleton s = still(h.spawn(EntityType.SKELETON, 2.5f, 1, 7.5f), 180.0f);
		s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		CombatState state = CombatStateManager.get(p);

		h.assertTrue(CombatDirector.requestRangedAttack(p, s), "skeleton should get a shooting turn");
		EnemySlot slot = state.enemies.get(s.getId());
		h.assertTrue(slot.ranged && slot.kind == TelegraphPayload.RANGED, "shot should have the ranged telegraph");
		h.assertTrue(!CombatDirector.requestRangedAttack(p, s), "no second shot while one is lined up");
		h.succeedWhen(() -> h.assertTrue(
				!h.getLevel().getEntitiesOfClass(AbstractArrow.class, p.getBoundingBox().inflate(12)).isEmpty(),
				"the skeleton should fire when the warning ends"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void counterDeflectsRangedShot(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Skeleton s = still(h.spawn(EntityType.SKELETON, 2.5f, 1, 7.5f), 180.0f);
		CombatState state = CombatStateManager.get(p);

		CombatDirector.requestRangedAttack(p, s); // 5 blocks away: outside melee counter range
		counter(p);
		EnemySlot slot = state.enemies.get(s.getId());
		h.assertTrue(!slot.isTelegraphing(), "counter should deflect a ranged shot at any distance");
		h.assertTrue(state.combo == 1, "a deflect counts toward the combo");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void piglinsTakeTurnsToo(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Piglin piglin = still(h.spawn(EntityType.PIGLIN, 2.5f, 1, 3.5f), 180.0f);
		CombatState state = CombatStateManager.get(p);

		h.assertTrue(!piglin.doHurtTarget(p), "a piglin's direct hit should become a turn request");
		EnemySlot slot = state.enemies.get(piglin.getId());
		h.assertTrue(slot != null && slot.isTelegraphing(), "the piglin's attack should be telegraphed");
		h.succeedWhen(() -> h.assertTrue(!slot.isTelegraphing() && piglin.swinging,
				"the director should perform the piglin's attack after the warning"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesGetShortStunsAndNoKnockdown(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		IronGolem golem = still(h.spawn(EntityType.IRON_GOLEM, 2.5f, 1, 4.5f), 180.0f);
		CombatState state = CombatStateManager.get(p);
		long now = h.getLevel().getGameTime();

		EnemyStatus.stun(p, state, golem, 40);
		EnemySlot slot = state.enemies.get(golem.getId());
		h.assertTrue(slot.stunnedUntil - now <= 10, "bosses are only stunned briefly");
		EnemyStatus.knockDown(p, state, golem, 60);
		h.assertTrue(!slot.isKnockedDown(now), "bosses can't be knocked down");
		h.succeed();
	}

	// --- Milestone 10: final hit detection (drives the cinematic finisher) ---

	@GameTest(template = EMPTY_STRUCTURE)
	public void killingLastEnemyIsFinalHit(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 4.0);
		z.setHealth(1.0f);
		z.setTarget(p);
		CombatState state = CombatStateManager.get(p);

		strike(p, FACING_PLUS_Z, 0, 0);
		h.succeedWhen(() -> h.assertTrue(state.lastFinalHitTick >= 0, "killing the last enemy is the final hit"));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void killWithEnemiesLeftIsNotFinalHit(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		Zombie z = zombie(h, 2.5, 4.0);
		z.setHealth(1.0f);
		z.setTarget(p);
		Zombie other = zombie(h, 5.5, 6.5);
		other.setTarget(p); // still fighting
		CombatState state = CombatStateManager.get(p);

		strike(p, FACING_PLUS_Z, 0, 0);
		h.runAfterDelay(10, () -> {
			h.assertTrue(!z.isAlive(), "the weak zombie should die");
			h.assertTrue(state.lastFinalHitTick < 0, "not the final hit while another enemy is fighting");
			h.succeed();
		});
	}

	// --- Milestone 11: animations ---

	/** Every animation the server can trigger must exist in the animation file and parse. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void combatAnimationsParse(GameTestHelper h) {
		String path = "/assets/freeflow/player_animations/freeflow_combat.json";
		try (var in = FreeflowGameTests.class.getResourceAsStream(path)) {
			h.assertTrue(in != null, "animation file missing: " + path);
			var json = com.google.gson.JsonParser.parseReader(
					new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			var animations = dev.kosmx.playerAnim.core.data.gson.GeckoLibSerializer.deserialize(json);
			java.util.Map<String, Integer> lengths = new java.util.HashMap<>();
			animations.forEach(a -> lengths.put(a.getName(), a.getLength()));
			for (String name : com.borislav.freeflow.combat.Anim.ALL) {
				h.assertTrue(lengths.containsKey(name), "animation not found or failed to parse: " + name);
				h.assertTrue(lengths.get(name) > 0, "animation has no length: " + name);
			}
		} catch (java.io.IOException e) {
			h.fail("could not read animations: " + e);
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strikeMovesVaryWithDistanceAndCombo(GameTestHelper h) {
		var Anim = new Object() {
			String pick(int index, int travel, int combo) {
				return com.borislav.freeflow.combat.Anim.chooseStrike(index, travel, combo);
			}
		};
		String longLunge = Anim.pick(0, 4, 0);
		h.assertTrue(longLunge.startsWith("flying_"), "long lunges should be flying attacks, got " + longLunge);
		java.util.Set<String> low = new java.util.HashSet<>();
		java.util.Set<String> high = new java.util.HashSet<>();
		for (int i = 0; i < 12; i++) {
			low.add(Anim.pick(i, 0, 0));
			high.add(Anim.pick(i, 0, 15));
		}
		h.assertTrue(low.size() >= 3, "low-combo strings should still vary, got " + low);
		h.assertTrue(high.contains("spin_backfist") && high.contains("haymaker"),
				"high combos should unlock flashy moves, got " + high);
		h.assertTrue(com.borislav.freeflow.combat.Anim.strength("haymaker") == com.borislav.freeflow.combat.Anim.HEAVY,
				"haymaker is a heavy hit");
		h.assertTrue(com.borislav.freeflow.combat.Anim.strength("jab_right") == com.borislav.freeflow.combat.Anim.LIGHT,
				"jab is a light hit");
		h.succeed();
	}

	private static void counter(ServerPlayer p) {
		CombatStateManager.handleInput(p, new CombatInputPayload(CombatAction.COUNTER, 0, 0, FACING_PLUS_Z));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void counterWithNothingLocksOut(GameTestHelper h) {
		ServerPlayer p = player(h, CombatMode.FORCED_ON);
		CombatState state = CombatStateManager.get(p);

		CombatStateManager.handleInput(p, new CombatInputPayload(CombatAction.COUNTER, 0, 0, FACING_PLUS_Z));
		h.assertTrue(state.counterCooldownUntil > h.getLevel().getGameTime(), "whiffed counter should lock out");
		int acked = state.acceptedInputs;
		CombatStateManager.handleInput(p, new CombatInputPayload(CombatAction.COUNTER, 0, 0, FACING_PLUS_Z));
		h.assertTrue(state.acceptedInputs == acked, "counter spam during lockout should be ignored");
		h.succeed();
	}
}
