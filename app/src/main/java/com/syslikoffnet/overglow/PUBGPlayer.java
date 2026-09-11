package com.syslikoffnet.overglow;

/**
 * Игрок PUBG Mobile (Фаза 1: Мобильное управление и TPP-камера):
 * - Интеграция CharacterMotor (независимое вращение тела и расчет физики)
 * - Интеграция SpringArmCamera (орбитальная камера с коллизиями и сменой плеча)
 * - Полет на самолете C-130, свободное падение (Freefall), парашют
 * - Сбор лута, экипировка, аптечки, энергетики, гранаты, вождение транспорта
 * - Стоечный цикл в стиле PUBG: прыжок из prone → crouch → stand
 */
public final class PUBGPlayer {

    public static final int MODE_IN_PLANE = 0;
    public static final int MODE_FREEFALL = 1;
    public static final int MODE_PARACHUTE = 2;
    public static final int MODE_ON_FOOT = 3;
    public static final int MODE_DRIVING = 4;

    public int moveMode = MODE_IN_PLANE;

    // Мотор персонажа и TPP Камера
    public final CharacterMotor motor = new CharacterMotor();
    public final SpringArmCamera camera = new SpringArmCamera();
    public final RecoilController recoil = new RecoilController();

    // Быстрый доступ к координатам для совместимости
    public final Math3D.Vec3 pos = motor.position;
    public final Math3D.Vec3 vel = motor.velocity;

    public float yaw = 0;
    public float pitch = 0;

    public boolean isTPP = true;

    public float health = 100f;
    public float boost = 0f;
    public int helmetLevel = 0;
    public int vestLevel = 0;
    public int backpackLevel = 1;

    public int kills = 0;
    public boolean isDead = false;

    public final Weapon[] weapons = new Weapon[5];
    public int activeSlot = 0;

    public int firstAidCount = 0;
    public int energyDrinkCount = 0;
    public int grenadeCount = 0;
    public boolean hasPan = false;

    public float healTimer = 0f;
    public float maxHealTimer = 0f;
    public float boostUseTimer = 0f;

    public boolean isCrouching = false;
    public boolean isProning = false;
    public boolean isSprinting = false;
    public boolean isAiming = false;
    public float adsFactor = 0f;

    // Пик-наклон (peek) -1..1 и цель от кнопок
    public float leanAngle = 0f;
    public float leanTarget = 0f;
    public float leanHold = 0f;            // наклон удержанием кнопки
    public boolean leanLockedLeft = false; // фиксация тапом
    public boolean leanLockedRight = false;

    public float fallSpeed = 0f;
    public float altitude = 180f;

    public Vehicle3D currentVehicle = null;

    // Контекст взаимодействия (для кнопки на HUD)
    public InteractiveDoor nearDoor = null;
    public Vehicle3D nearVehicle = null;
    public float interactDistance = 0f;

    public float recoilPitch = 0f;
    public float recoilYaw = 0f;

    public float hitMarkerTimer = 0f;
    public boolean hitMarkerHeadshot = false;
    public float damageIndicatorTimer = 0f;
    public float damageAngle = 0f;

    public PUBGPlayer() {}

    public void jumpFromPlane(Math3D.Vec3 planePos, float planeYaw) {
        moveMode = MODE_FREEFALL;
        pos.set(planePos);
        yaw = planeYaw;
        camera.yaw = planeYaw;
        camera.pitch = -25f;
        altitude = planePos.y;
        vel.set(0, -35f, 0);
        SoundSynth3D.play2D(SoundSynth3D.SOUND_EXPLOSION, 0.5f);
    }

    public void openParachute() {
        if (moveMode == MODE_FREEFALL) {
            moveMode = MODE_PARACHUTE;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_PARACHUTE_DEPLOY, 1.0f);
        }
    }

    public void update(float dt, PUBGMap map, float moveX, float moveZ, boolean sprintInput) {
        if (isDead) return;

        // Синхронизация yaw/pitch камеры и игрока
        this.yaw = camera.yaw;
        this.pitch = camera.pitch;

        if (boost > 0) {
            boost = Math3D.clamp(boost - dt * 1.2f, 0f, 100f);
            health = Math.min(100f, health + dt * 2.5f);
        }

        if (healTimer > 0) {
            healTimer -= dt;
            if (healTimer <= 0) {
                health = Math.min(100f, health + 75f);
                SoundSynth3D.play2D(SoundSynth3D.SOUND_VICTORY, 0.4f);
            }
        }
        if (boostUseTimer > 0) {
            boostUseTimer -= dt;
            if (boostUseTimer <= 0) {
                boost = Math.min(100f, boost + 45f);
            }
        }

        Weapon activeW = getActiveWeapon();
        if (activeW != null) {
            activeW.update(dt);
            recoil.update(dt, activeW);
            recoilPitch = recoil.currentPitchOffset;
            recoilYaw = recoil.currentYawOffset;
        }

        if (damageIndicatorTimer > 0) damageIndicatorTimer -= dt;
        if (hitMarkerTimer > 0) hitMarkerTimer -= dt;

        switch (moveMode) {
            case MODE_IN_PLANE:
                pos.set(map.planePos);
                break;
            case MODE_FREEFALL:
                updateFreefall(dt, map, moveX, moveZ);
                break;
            case MODE_PARACHUTE:
                updateParachute(dt, map, moveX, moveZ);
                break;
            case MODE_ON_FOOT:
                updateOnFoot(dt, map, moveX, moveZ, sprintInput);
                break;
            case MODE_DRIVING:
                updateDriving(dt, map);
                break;
        }

        isCrouching = (motor.currentStance == CharacterMotor.STANCE_CROUCH);
        isProning = (motor.currentStance == CharacterMotor.STANCE_PRONE);
        isSprinting = motor.isSprinting;

        // Плавный пик-наклон: зафиксированный тапом приоритетнее удержания
        float leanLock = (leanLockedLeft ? -1f : 0f) + (leanLockedRight ? 1f : 0f);
        float leanCur = (leanLock != 0f) ? leanLock : leanHold;
        leanAngle = Math3D.lerp(leanAngle, leanCur, dt * 10f);

        camera.update(dt, pos, motor.currentHeight, isAiming, isProning, isCrouching, leanAngle, map);
        this.adsFactor = camera.adsFactor;

        float distToZone = Math3D.dist(pos.x, 0, pos.z, map.blueZoneX, 0, map.blueZoneZ);
        if (distToZone > map.blueZoneRadius && moveMode != MODE_IN_PLANE) {
            takeDamage(5.5f * dt * map.zonePhase, null);
        }
    }

    private void updateFreefall(float dt, PUBGMap map, float moveX, float moveZ) {
        float targetSpeed = (camera.pitch < -40f) ? 234f : ((camera.pitch < -15f) ? 180f : 135f);
        fallSpeed = Math3D.lerp(fallSpeed, targetSpeed, dt * 4f);

        float rad = camera.yaw * Math3D.TO_RAD;
        float forwardSpd = (camera.pitch > -35f) ? 42f : 18f;

        float targetVelX = (float) Math.sin(rad) * (moveZ * forwardSpd + 20f) - (float) Math.cos(rad) * (moveX * 28f);
        float targetVelZ = (float) Math.cos(rad) * (moveZ * forwardSpd + 20f) + (float) Math.sin(rad) * (moveX * 28f);

        vel.x = Math3D.lerp(vel.x, targetVelX, dt * 5f);
        vel.z = Math3D.lerp(vel.z, targetVelZ, dt * 5f);
        vel.y = -(fallSpeed / 3.6f);

        pos.x += vel.x * dt;
        pos.y += vel.y * dt;
        pos.z += vel.z * dt;

        float gHeight = map.getTerrainHeight(pos.x, pos.z);
        altitude = Math.max(0, pos.y - gHeight);

        if (pos.y <= gHeight + 50f) {
            openParachute();
        }
    }

    private void updateParachute(float dt, PUBGMap map, float moveX, float moveZ) {
        fallSpeed = 24f;

        float rad = camera.yaw * Math3D.TO_RAD;
        float targetVelX = (float) Math.sin(rad) * (moveZ * 14f + 8f) - (float) Math.cos(rad) * (moveX * 14f);
        float targetVelZ = (float) Math.cos(rad) * (moveZ * 14f + 8f) + (float) Math.sin(rad) * (moveX * 14f);

        vel.x = Math3D.lerp(vel.x, targetVelX, dt * 8f);
        vel.z = Math3D.lerp(vel.z, targetVelZ, dt * 8f);
        vel.y = -6.5f;

        pos.x += vel.x * dt;
        pos.y += vel.y * dt;
        pos.z += vel.z * dt;

        float gHeight = map.getTerrainHeight(pos.x, pos.z);
        altitude = Math.max(0, pos.y - gHeight);

        if (pos.y <= gHeight + 0.2f) {
            pos.y = gHeight;
            moveMode = MODE_ON_FOOT;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_STEP, 0.8f);
        }
    }

    private void updateOnFoot(float dt, PUBGMap map, float moveX, float moveZ, boolean sprintInput) {
        motor.update(dt, moveX, moveZ, camera.yaw, sprintInput, isAiming, map);

        // Сбор лута рядом (автоподбор в радиусе, как в мобильной королевской битве)
        for (LootItem item : map.loot) {
            if (!item.isTaken && Math3D.distSq(pos.x, pos.y, pos.z, item.x, item.y, item.z) < 1.8f * 1.8f) {
                pickupItem(item);
            }
        }

        updateInteractContext(map);
    }

    /** Контекст для кнопки взаимодействия: дверь / транспорт */
    private void updateInteractContext(PUBGMap map) {
        nearDoor = null;
        nearVehicle = null;
        float best = 2.6f;
        for (InteractiveDoor d : map.doors) {
            float dd = Math3D.dist(pos.x, pos.y, pos.z, d.hingeX + 0.8f, d.hingeY, d.hingeZ);
            if (dd < best) { best = dd; nearDoor = d; }
        }
        float bestV = 4.0f;
        for (Vehicle3D v : map.vehicles) {
            if (v.hasDriver || v.destroyed) continue;
            float dd = Math3D.dist(pos.x, pos.y, pos.z, v.pos.x, v.pos.y, v.pos.z);
            if (dd < bestV) { bestV = dd; nearVehicle = v; }
        }
        interactDistance = Math.min(best, bestV);
    }

    private void updateDriving(float dt, PUBGMap map) {
        if (currentVehicle != null) {
            currentVehicle.hasDriver = true;
            pos.set(currentVehicle.pos.x, currentVehicle.pos.y + 0.6f, currentVehicle.pos.z);
            motor.bodyYaw = currentVehicle.yaw;
            altitude = pos.y - map.getTerrainHeight(pos.x, pos.z);
        }
    }

    // =========================================================================
    // Стейк-кнопки в духе мобильной BR: прыжок/присед/лёжа с циклом стоек
    // =========================================================================

    public void pressJump() {
        if (moveMode != MODE_ON_FOOT) return;
        if (motor.currentStance == CharacterMotor.STANCE_PRONE) {
            motor.setStance(CharacterMotor.STANCE_CROUCH);
        } else if (motor.currentStance == CharacterMotor.STANCE_CROUCH) {
            motor.setStance(CharacterMotor.STANCE_STAND);
        } else {
            motor.jump();
        }
    }

    public void pressCrouch() {
        if (moveMode != MODE_ON_FOOT) return;
        motor.setStance(motor.currentStance == CharacterMotor.STANCE_CROUCH
                ? CharacterMotor.STANCE_STAND : CharacterMotor.STANCE_CROUCH);
    }

    public void pressProne() {
        if (moveMode != MODE_ON_FOOT) return;
        motor.setStance(motor.currentStance == CharacterMotor.STANCE_PRONE
                ? CharacterMotor.STANCE_STAND : CharacterMotor.STANCE_PRONE);
    }

    /** Кнопка ADS: отпустить прицел / взять (только с оружием в руках) */
    public void toggleAim() {
        if (getActiveWeapon() == null) return;
        isAiming = !isAiming;
    }

    public void setPeek(float dir, boolean lock) {
        if (dir < 0) leanLockedLeft = lock ? !leanLockedLeft : false;
        else leanLockedRight = lock ? !leanLockedRight : false;
    }

    public void reloadActive() {
        Weapon w = getActiveWeapon();
        if (w != null) w.startReload();
    }

    public void swapSlots(int slot) {
        if (slot < 0 || slot >= weapons.length || slot == activeSlot) return;
        if (weapons[slot] == null) return;
        activeSlot = slot;
        isAiming = false;
        SoundSynth3D.play2D(SoundSynth3D.SOUND_RELOAD, 0.5f);
    }

    /** Подготовка гранаты: true, если есть что бросать (игра спавнит снаряд) */
    public boolean armGrenade() {
        if (grenadeCount <= 0 || moveMode != MODE_ON_FOOT) return false;
        grenadeCount--;
        return true;
    }

    public void useMedkit() {
        if (firstAidCount > 0 && health < 100f && healTimer <= 0) {
            firstAidCount--;
            healTimer = 5.0f;
            maxHealTimer = 5.0f;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_BANDAGE, 0.8f);
        }
    }

    public void useDrink() {
        if (energyDrinkCount > 0 && boost < 100f && boostUseTimer <= 0) {
            energyDrinkCount--;
            boostUseTimer = 4.0f;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DRINK_OPEN, 0.9f);
        }
    }

    public Weapon getActiveWeapon() {
        return weapons[activeSlot];
    }

    // =========================================================================
    // Взаимодействие
    // =========================================================================

    /** Универсальная кнопка Interaction: выход/посадка в транспорт, иначе дверь */
    public void interact(PUBGMap map) {
        if (moveMode == MODE_DRIVING) {
            enterExitVehicle(map);
            return;
        }
        if (nearVehicle != null) {
            currentVehicle = nearVehicle;
            moveMode = MODE_DRIVING;
            motor.velocity.set(0, 0, 0);
            isAiming = false;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DOOR_OPEN, 0.8f);
            return;
        }
        if (nearDoor != null) {
            nearDoor.toggle();
        }
    }

    public void enterExitVehicle(PUBGMap map) {
        if (moveMode == MODE_DRIVING) {
            if (currentVehicle != null) {
                currentVehicle.hasDriver = false;
                // Высадка слева от машины
                float rad = currentVehicle.yaw * Math3D.TO_RAD;
                float ex = currentVehicle.pos.x + (float) Math.cos(rad) * 2.2f;
                float ez = currentVehicle.pos.z - (float) Math.sin(rad) * 2.2f;
                pos.set(ex, map.getTerrainHeight(ex, ez) + 0.05f, ez);
            }
            currentVehicle = null;
            moveMode = MODE_ON_FOOT;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DOOR_CLOSE, 0.7f);
        } else {
            for (Vehicle3D v : map.vehicles) {
                if (!v.hasDriver && !v.destroyed
                        && Math3D.dist(pos.x, pos.y, pos.z, v.pos.x, v.pos.y, v.pos.z) < 4.0f) {
                    currentVehicle = v;
                    moveMode = MODE_DRIVING;
                    isAiming = false;
                    SoundSynth3D.play2D(SoundSynth3D.SOUND_DOOR_OPEN, 0.8f);
                    break;
                }
            }
        }
    }

    public void interactDoors(PUBGMap map) {
        for (InteractiveDoor d : map.doors) {
            if (Math3D.dist(pos.x, pos.y, pos.z, d.hingeX, d.hingeY, d.hingeZ) < 2.5f) {
                d.toggle();
                break;
            }
        }
    }

    // =========================================================================
    // Лут
    // =========================================================================

    public void pickupItem(LootItem item) {
        item.isTaken = true;
        SoundSynth3D.play2D(SoundSynth3D.SOUND_RELOAD, 0.6f);

        switch (item.type) {
            case LootItem.TYPE_WEAPON:
                if (item.subId == Weapon.ID_GRENADE) {
                    grenadeCount = Math.min(5, grenadeCount + 1);
                    break;
                }
                if (weapons[0] == null) weapons[0] = Weapon.create(item.subId);
                else if (weapons[1] == null) weapons[1] = Weapon.create(item.subId);
                else weapons[activeSlot] = Weapon.create(item.subId);
                break;
            case LootItem.TYPE_AMMO:
                // Пополняем резерв всех огнестрельных слотов
                for (Weapon w : weapons) {
                    if (w != null && !w.isMelee && !w.isProjectile) {
                        w.ammoReserve = Math.min(w.maxReserve, w.ammoReserve + item.count);
                    }
                }
                break;
            case LootItem.TYPE_MEDKIT:
                firstAidCount = Math.min(5, firstAidCount + 1);
                break;
            case LootItem.TYPE_ENERGY_DRINK:
                energyDrinkCount = Math.min(6, energyDrinkCount + 1);
                break;
            case LootItem.TYPE_HELMET:
                helmetLevel = Math.max(helmetLevel, item.subId);
                break;
            case LootItem.TYPE_VEST:
                vestLevel = Math.max(vestLevel, item.subId);
                break;
            case LootItem.TYPE_BACKPACK:
                backpackLevel = Math.max(backpackLevel, item.subId);
                break;
            case LootItem.TYPE_PAN:
                hasPan = true;
                if (weapons[2] == null) weapons[2] = Weapon.create(Weapon.ID_KNIFE);
                SoundSynth3D.play2D(SoundSynth3D.SOUND_PAN, 0.9f);
                break;
            case LootItem.TYPE_ATTACHMENT:
                Weapon active = getActiveWeapon();
                if (active != null) {
                    active.attach(WeaponAttachment.create(item.subId));
                    SoundSynth3D.play2D(SoundSynth3D.SOUND_RELOAD, 0.8f);
                } else {
                    // Примеряем на любое оружие в слотах
                    for (Weapon w : weapons) {
                        if (w != null) { w.attach(WeaponAttachment.create(item.subId)); break; }
                    }
                }
                break;
        }
    }

    public void takeDamage(float dmg, Math3D.Vec3 attackerPos) {
        if (isDead) return;

        float armorMul = 1.0f - (vestLevel * 0.18f);
        dmg *= armorMul;

        health -= dmg;
        damageIndicatorTimer = 0.6f;
        camera.punch(Math3D.clamp(dmg * 0.06f, 0.2f, 1.4f));

        if (attackerPos != null) {
            float dx = attackerPos.x - pos.x;
            float dz = attackerPos.z - pos.z;
            float angleToAttacker = (float) Math.atan2(dx, dz) * Math3D.TO_DEG;
            damageAngle = angleToAttacker - camera.yaw;
        }

        SoundSynth3D.play2D(SoundSynth3D.SOUND_BODY_HIT, 0.8f);

        if (health <= 0) {
            health = 0;
            isDead = true;
            SoundSynth3D.play2D(SoundSynth3D.SOUND_DEFEAT, 1.0f);
        }
    }
}
