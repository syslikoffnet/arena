package com.syslikoffnet.overglow;

import java.util.ArrayList;

/**
 * Фаза 1: Физическая система коллизий с объектами мира (World Collider):
 * - Проверка и выталкивание цилиндра персонажа из стен, домов и закрытых дверей
 * - Raycast проверка попаданий пуль в стены и дома
 * - 0 GC Alloc за кадр
 */
public final class WorldCollider {

    private WorldCollider() {}

    /**
     * Разрешение коллизии цилиндра (персонаж/бот) со всеми препятствиями и закрытыми дверями карты
     */
    public static void resolveCharacterCollision(Math3D.Vec3 pos, float radius, float height, PUBGMap map) {
        if (map == null) return;

        float footY = pos.y;
        float headY = pos.y + height;

        // 1. Коллизии со стенами и домами
        for (PUBGMap.Obstacle obs : map.obstacles) {
            Math3D.Box b = obs.box;
            if (headY <= b.minY || footY >= b.maxY) continue;

            // Ближайшая точка на AABB к центру персонажа
            float closestX = Math3D.clamp(pos.x, b.minX, b.maxX);
            float closestZ = Math3D.clamp(pos.z, b.minZ, b.maxZ);

            float dx = pos.x - closestX;
            float dz = pos.z - closestZ;
            float distSq = dx * dx + dz * dz;

            if (distSq < radius * radius) {
                float dist = (float) Math.sqrt(distSq);
                if (dist > 0.0001f) {
                    float penetration = radius - dist;
                    pos.x += (dx / dist) * penetration;
                    pos.z += (dz / dist) * penetration;
                } else {
                    // Персонаж внутри бокса — выталкиваем по наименьшей оси
                    float pushLeft = Math.abs(pos.x - b.minX);
                    float pushRight = Math.abs(b.maxX - pos.x);
                    float pushDown = Math.abs(pos.z - b.minZ);
                    float pushUp = Math.abs(b.maxZ - pos.z);

                    float minPush = Math.min(Math.min(pushLeft, pushRight), Math.min(pushDown, pushUp));
                    if (minPush == pushLeft) pos.x = b.minX - radius;
                    else if (minPush == pushRight) pos.x = b.maxX + radius;
                    else if (minPush == pushDown) pos.z = b.minZ - radius;
                    else pos.z = b.maxZ + radius;
                }
            }
        }

        // 2. Коллизии с закрытыми дверями
        for (InteractiveDoor door : map.doors) {
            if (door.isOpen) continue;
            Math3D.Box b = door.collider;
            if (headY <= b.minY || footY >= b.maxY) continue;

            float closestX = Math3D.clamp(pos.x, b.minX, b.maxX);
            float closestZ = Math3D.clamp(pos.z, b.minZ, b.maxZ);

            float dx = pos.x - closestX;
            float dz = pos.z - closestZ;
            float distSq = dx * dx + dz * dz;

            if (distSq < radius * radius) {
                float dist = (float) Math.sqrt(distSq);
                if (dist > 0.0001f) {
                    float penetration = radius - dist;
                    pos.x += (dx / dist) * penetration;
                    pos.z += (dz / dist) * penetration;
                }
            }
        }
    }

    /**
     * Проверка прохождения луча (Raycast) через стены и дома
     */
    public static float raycastWorld(Math3D.Vec3 rayOrigin, Math3D.Vec3 rayDir, float maxDist, PUBGMap map) {
        if (map == null) return maxDist;
        float closestHit = maxDist;

        for (PUBGMap.Obstacle obs : map.obstacles) {
            float dist = obs.box.raycast(rayOrigin, rayDir);
            if (dist > 0 && dist < closestHit) {
                closestHit = dist;
            }
        }

        for (InteractiveDoor door : map.doors) {
            if (!door.isOpen) {
                float dist = door.collider.raycast(rayOrigin, rayDir);
                if (dist > 0 && dist < closestHit) {
                    closestHit = dist;
                }
            }
        }

        return closestHit;
    }
}
