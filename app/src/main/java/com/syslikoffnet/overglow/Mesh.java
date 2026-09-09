package com.syslikoffnet.overglow;

import android.opengl.GLES20;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * 3D Полигональная сетка (вершины, нормали, UV-координаты, индексы).
 */
public final class Mesh {

    public FloatBuffer vertexBuffer;
    public FloatBuffer normalBuffer;
    public FloatBuffer uvBuffer;
    public ShortBuffer indexBuffer;
    public int indexCount;

    public Mesh(float[] vertices, float[] normals, float[] uvs, short[] indices) {
        this.vertexBuffer = GLUtil.createFloatBuffer(vertices);
        this.normalBuffer = GLUtil.createFloatBuffer(normals);
        this.uvBuffer = GLUtil.createFloatBuffer(uvs);
        this.indexBuffer = GLUtil.createShortBuffer(indices);
        this.indexCount = indices.length;
    }

    public void draw(int posAttr, int normAttr, int uvAttr) {
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(posAttr, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(posAttr);

        if (normAttr >= 0 && normalBuffer != null) {
            normalBuffer.position(0);
            GLES20.glVertexAttribPointer(normAttr, 3, GLES20.GL_FLOAT, false, 0, normalBuffer);
            GLES20.glEnableVertexAttribArray(normAttr);
        }

        if (uvAttr >= 0 && uvBuffer != null) {
            uvBuffer.position(0);
            GLES20.glVertexAttribPointer(uvAttr, 2, GLES20.GL_FLOAT, false, 0, uvBuffer);
            GLES20.glEnableVertexAttribArray(uvAttr);
        }

        indexBuffer.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        GLES20.glDisableVertexAttribArray(posAttr);
        if (normAttr >= 0) GLES20.glDisableVertexAttribArray(normAttr);
        if (uvAttr >= 0) GLES20.glDisableVertexAttribArray(uvAttr);
    }
}
