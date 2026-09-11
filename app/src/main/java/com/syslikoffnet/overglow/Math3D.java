package com.syslikoffnet.overglow;

/**
 * 3D Векторы, Матрицы, Лучи и AABB-коллизии для 3D шутера.
 * Оптимизировано для быстрой работы без сборщика мусора.
 */
public final class Math3D {

    public static final float PI = (float) Math.PI;
    public static final float TO_RAD = PI / 180f;
    public static final float TO_DEG = 180f / PI;

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Плавный доворот угла по кратайшей дуге (градусы), t = 0..1 */
    public static float lerpAngle(float a, float b, float t) {
        float diff = ((b - a) % 360f + 540f) % 360f - 180f;
        return a + diff * clamp(t, 0f, 1f);
    }

    public static float smoothstep(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float distSq(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    public static float dist(float x1, float y1, float z1, float x2, float y2, float z2) {
        return (float) Math.sqrt(distSq(x1, y1, z1, x2, y2, z2));
    }

    // =========================================================================
    // Вектор 3D
    // =========================================================================
    public static final class Vec3 {
        public float x, y, z;

        public Vec3() {}

        public Vec3(float x, float y, float z) {
            set(x, y, z);
        }

        public Vec3 set(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Vec3 set(Vec3 o) {
            this.x = o.x;
            this.y = o.y;
            this.z = o.z;
            return this;
        }

        public Vec3 add(float x, float y, float z) {
            this.x += x;
            this.y += y;
            this.z += z;
            return this;
        }

        public Vec3 add(Vec3 o) {
            return add(o.x, o.y, o.z);
        }

        public Vec3 sub(float x, float y, float z) {
            this.x -= x;
            this.y -= y;
            this.z -= z;
            return this;
        }

        public Vec3 sub(Vec3 o) {
            return sub(o.x, o.y, o.z);
        }

        public Vec3 mul(float s) {
            this.x *= s;
            this.y *= s;
            this.z *= s;
            return this;
        }

        /** Алиас умножения на скаляр (используется баллистикой) */
        public Vec3 scale(float s) {
            return mul(s);
        }

        public Vec3 scaled(float s, Vec3 out) {
            return out.set(x * s, y * s, z * s);
        }

        public float lengthSq() {
            return x * x + y * y + z * z;
        }

        public float length() {
            return (float) Math.sqrt(lengthSq());
        }

        public Vec3 normalize() {
            float len = length();
            if (len > 0.00001f) {
                float inv = 1f / len;
                x *= inv;
                y *= inv;
                z *= inv;
            }
            return this;
        }

        public float dot(Vec3 o) {
            return x * o.x + y * o.y + z * o.z;
        }

        public Vec3 cross(Vec3 o, Vec3 out) {
            out.x = y * o.z - z * o.y;
            out.y = z * o.x - x * o.z;
            out.z = x * o.y - y * o.x;
            return out;
        }
    }

    // =========================================================================
    // Матрица 4х4 (Column-Major для OpenGL)
    // =========================================================================
    public static final class Mat4 {
        public final float[] m = new float[16];

        public Mat4() {
            identity();
        }

        public Mat4 identity() {
            for (int i = 0; i < 16; i++) m[i] = 0;
            m[0] = m[5] = m[10] = m[15] = 1f;
            return this;
        }

        public Mat4 set(Mat4 o) {
            System.arraycopy(o.m, 0, m, 0, 16);
            return this;
        }

        public Mat4 perspective(float fovY, float aspect, float zNear, float zFar) {
            identity();
            float rad = fovY * TO_RAD;
            float tanHalfFov = (float) Math.tan(rad / 2f);
            m[0] = 1f / (aspect * tanHalfFov);
            m[5] = 1f / tanHalfFov;
            m[10] = -(zFar + zNear) / (zFar - zNear);
            m[11] = -1f;
            m[14] = -(2f * zFar * zNear) / (zFar - zNear);
            m[15] = 0f;
            return this;
        }

        public Mat4 lookAt(Vec3 eye, Vec3 target, Vec3 up) {
            float zx = eye.x - target.x;
            float zy = eye.y - target.y;
            float zz = eye.z - target.z;
            float zLen = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
            if (zLen > 0.00001f) {
                zx /= zLen;
                zy /= zLen;
                zz /= zLen;
            }

            // X = Up x Z
            float xx = up.y * zz - up.z * zy;
            float xy = up.z * zx - up.x * zz;
            float xz = up.x * zy - up.y * zx;
            float xLen = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
            if (xLen > 0.00001f) {
                xx /= xLen;
                xy /= xLen;
                xz /= xLen;
            }

            // Y = Z x X
            float yx = zy * xz - zz * xy;
            float yy = zz * xx - zx * xz;
            float yz = zx * xy - zy * xx;

            m[0] = xx; m[4] = xy; m[8] = xz;  m[12] = -(xx * eye.x + xy * eye.y + xz * eye.z);
            m[1] = yx; m[5] = yy; m[9] = yz;  m[13] = -(yx * eye.x + yy * eye.y + yz * eye.z);
            m[2] = zx; m[6] = zy; m[10] = zz; m[14] = -(zx * eye.x + zy * eye.y + zz * eye.z);
            m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f;
            return this;
        }

        public Mat4 multiply(Mat4 rhs) {
            float[] a = this.m;
            float[] b = rhs.m;
            float[] res = new float[16];
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    res[c * 4 + r] = a[r] * b[c * 4] +
                                     a[4 + r] * b[c * 4 + 1] +
                                     a[8 + r] * b[c * 4 + 2] +
                                     a[12 + r] * b[c * 4 + 3];
                }
            }
            System.arraycopy(res, 0, this.m, 0, 16);
            return this;
        }

        public Mat4 translate(float tx, float ty, float tz) {
            m[12] += m[0] * tx + m[4] * ty + m[8] * tz;
            m[13] += m[1] * tx + m[5] * ty + m[9] * tz;
            m[14] += m[2] * tx + m[6] * ty + m[10] * tz;
            m[15] += m[3] * tx + m[7] * ty + m[11] * tz;
            return this;
        }

        public Mat4 scale(float sx, float sy, float sz) {
            m[0] *= sx; m[1] *= sx; m[2] *= sx; m[3] *= sx;
            m[4] *= sy; m[5] *= sy; m[6] *= sy; m[7] *= sy;
            m[8] *= sz; m[9] *= sz; m[10] *= sz; m[11] *= sz;
            return this;
        }

        public Mat4 rotate(float angleDeg, float x, float y, float z) {
            float rad = angleDeg * TO_RAD;
            float c = (float) Math.cos(rad);
            float s = (float) Math.sin(rad);
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 0.00001f) {
                x /= len; y /= len; z /= len;
            }
            float nc = 1f - c;

            float r0 = x * x * nc + c;
            float r1 = y * x * nc + z * s;
            float r2 = z * x * nc - y * s;

            float r4 = x * y * nc - z * s;
            float r5 = y * y * nc + c;
            float r6 = z * y * nc + x * s;

            float r8 = x * z * nc + y * s;
            float r9 = y * z * nc - x * s;
            float r10 = z * z * nc + c;

            Mat4 rot = new Mat4();
            rot.m[0] = r0; rot.m[1] = r1; rot.m[2] = r2;
            rot.m[4] = r4; rot.m[5] = r5; rot.m[6] = r6;
            rot.m[8] = r8; rot.m[9] = r9; rot.m[10] = r10;

            return multiply(rot);
        }
    }

    // =========================================================================
    // 3D Axis-Aligned Bounding Box (AABB)
    // =========================================================================
    public static class Box {
        public float minX, minY, minZ;
        public float maxX, maxY, maxZ;

        public Box() {}

        public Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            set(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public Box set(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            return this;
        }

        public boolean intersects(Box o) {
            return (minX <= o.maxX && maxX >= o.minX) &&
                   (minY <= o.maxY && maxY >= o.minY) &&
                   (minZ <= o.maxZ && maxZ >= o.minZ);
        }

        public boolean contains(float x, float y, float z) {
            return x >= minX && x <= maxX &&
                   y >= minY && y <= maxY &&
                   z >= minZ && z <= maxZ;
        }

        /** Пересечение луча с AABB (возвращает дистанцию t или -1) */
        public float raycast(Vec3 origin, Vec3 dir) {
            float tmin = (minX - origin.x) / (Math.abs(dir.x) > 0.00001f ? dir.x : 0.00001f);
            float tmax = (maxX - origin.x) / (Math.abs(dir.x) > 0.00001f ? dir.x : 0.00001f);
            if (tmin > tmax) { float tmp = tmin; tmin = tmax; tmax = tmp; }

            float tymin = (minY - origin.y) / (Math.abs(dir.y) > 0.00001f ? dir.y : 0.00001f);
            float tymax = (maxY - origin.y) / (Math.abs(dir.y) > 0.00001f ? dir.y : 0.00001f);
            if (tymin > tymax) { float tmp = tymin; tymin = tymax; tymax = tmp; }

            if ((tmin > tymax) || (tymin > tmax)) return -1f;
            if (tymin > tmin) tmin = tymin;
            if (tymax < tmax) tmax = tymax;

            float tzmin = (minZ - origin.z) / (Math.abs(dir.z) > 0.00001f ? dir.z : 0.00001f);
            float tzmax = (maxZ - origin.z) / (Math.abs(dir.z) > 0.00001f ? dir.z : 0.00001f);
            if (tzmin > tzmax) { float tmp = tzmin; tzmin = tzmax; tzmax = tmp; }

            if ((tmin > tzmax) || (tzmin > tmax)) return -1f;
            if (tzmin > tmin) tmin = tzmin;
            if (tzmax < tmax) tmax = tzmax;

            return tmin > 0 ? tmin : (tmax > 0 ? tmax : -1f);
        }
    }

    /** Алиас AABB, используемый хитбоксами и баллистикой */
    public static final class AABB extends Box {
        public AABB() { super(); }
        public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            super(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
