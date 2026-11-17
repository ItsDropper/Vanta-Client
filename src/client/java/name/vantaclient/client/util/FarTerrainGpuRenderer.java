package name.vantaclient.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.NVMeshShader;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional, independent NV mesh-shader backend for FarView.
 *
 * Tile height/color grids are uploaded as shader-storage data. Each mesh task
 * handles at most an 8x8 cell meshlet; the mesh shader performs frustum and
 * near/far-band rejection before emitting triangles. No Nvidium classes or
 * internals are referenced. The caller falls back to Minecraft's VertexConsumer
 * renderer whenever this backend is unavailable or cannot initialize.
 *
 * All OpenGL access is restricted to the world-render callback on the render
 * thread. GPU allocations are bounded and released on world changes/shutdown.
 */
final class FarTerrainGpuRenderer {
    private static final int MESH_SHADER_NV = 0x9559;
    private static final int MAX_MESHLET_CELLS = 8;
    private static final int MAX_GPU_BYTES = 96 * 1024 * 1024;
    private static final String MESH_SHADER = """
            #version 450 core
            #extension GL_NV_mesh_shader : require

            layout(local_size_x = 32) in;
            layout(triangles, max_vertices = 81, max_primitives = 128) out;

            layout(std430, binding = 0) readonly buffer TerrainVertices {
                uvec2 terrainVertices[];
            };
            layout(std430, binding = 1) readonly buffer MeshletMetadata {
                ivec4 meshletMetadata[];
            };
            layout(std430, binding = 2) readonly buffer MeshletSizes {
                ivec4 meshletSizes[];
            };
            layout(std430, binding = 3) readonly buffer MeshletTransforms {
                vec4 meshletTransforms[];
            };
            layout(std430, binding = 4) readonly buffer MeshletBounds {
                vec4 meshletBounds[];
            };

            uniform mat4 uMvp;
            uniform vec2 uPlayerXZ;
            uniform float uInnerRadius;
            uniform float uOuterRadius;

            out vec4 vertexColor[];

            bool outsidePlane(vec4 plane, vec3 center, float radius) {
                return dot(plane, vec4(center, 1.0)) < -radius * length(plane.xyz);
            }

            void main() {
                uint meshlet = gl_WorkGroupID.x;
                ivec4 meta = meshletMetadata[meshlet];
                ivec4 sizeInfo = meshletSizes[meshlet];
                vec4 transform = meshletTransforms[meshlet];
                vec4 bounds = meshletBounds[meshlet];

                vec3 center = bounds.xyz;
                float radius = bounds.w;
                vec4 row0 = vec4(uMvp[0][0], uMvp[1][0], uMvp[2][0], uMvp[3][0]);
                vec4 row1 = vec4(uMvp[0][1], uMvp[1][1], uMvp[2][1], uMvp[3][1]);
                vec4 row2 = vec4(uMvp[0][2], uMvp[1][2], uMvp[2][2], uMvp[3][2]);
                vec4 row3 = vec4(uMvp[0][3], uMvp[1][3], uMvp[2][3], uMvp[3][3]);

                bool culled =
                    outsidePlane(row3 + row0, center, radius) ||
                    outsidePlane(row3 - row0, center, radius) ||
                    outsidePlane(row3 + row1, center, radius) ||
                    outsidePlane(row3 - row1, center, radius) ||
                    outsidePlane(row3 + row2, center, radius) ||
                    outsidePlane(row3 - row2, center, radius);

                float playerDistance = distance(center.xz, uPlayerXZ);
                culled = culled || playerDistance - radius > uOuterRadius;
                culled = culled || playerDistance + radius < uInnerRadius;

                if (culled) {
                    if (gl_LocalInvocationIndex == 0u) gl_PrimitiveCountNV = 0u;
                    return;
                }

                int cellsX = sizeInfo.x;
                int cellsZ = sizeInfo.y;
                int vertexWidth = cellsX + 1;
                int vertexCount = vertexWidth * (cellsZ + 1);
                int primitiveCount = cellsX * cellsZ * 2;

                for (int vertex = int(gl_LocalInvocationIndex);
                     vertex < vertexCount; vertex += int(gl_WorkGroupSize.x)) {
                    int localX = vertex % vertexWidth;
                    int localZ = vertex / vertexWidth;
                    int gridX = meta.z + localX;
                    int gridZ = meta.w + localZ;
                    uint dataIndex = uint(meta.x + gridZ * meta.y + gridX);
                    uvec2 data = terrainVertices[dataIndex];
                    float y = uintBitsToFloat(data.x);
                    vec3 position = vec3(
                        transform.x + float(gridX) * transform.z,
                        y,
                        transform.y + float(gridZ) * transform.w
                    );

                    gl_MeshVerticesNV[vertex].gl_Position = uMvp * vec4(position, 1.0);
                    uint color = data.y;
                    vertexColor[vertex] = vec4(
                        float((color >> 16u) & 255u),
                        float((color >> 8u) & 255u),
                        float(color & 255u),
                        255.0
                    ) / 255.0;
                }

                for (int cell = int(gl_LocalInvocationIndex);
                     cell < cellsX * cellsZ; cell += int(gl_WorkGroupSize.x)) {
                    int x = cell % cellsX;
                    int z = cell / cellsX;
                    uint v00 = uint(z * vertexWidth + x);
                    uint v10 = v00 + 1u;
                    uint v01 = v00 + uint(vertexWidth);
                    uint v11 = v01 + 1u;
                    uint primitive = uint(cell * 2);
                    gl_PrimitiveIndicesNV[primitive * 3u] = v00;
                    gl_PrimitiveIndicesNV[primitive * 3u + 1u] = v01;
                    gl_PrimitiveIndicesNV[primitive * 3u + 2u] = v11;
                    gl_PrimitiveIndicesNV[(primitive + 1u) * 3u] = v00;
                    gl_PrimitiveIndicesNV[(primitive + 1u) * 3u + 1u] = v11;
                    gl_PrimitiveIndicesNV[(primitive + 1u) * 3u + 2u] = v10;
                }

                if (gl_LocalInvocationIndex == 0u) {
                    gl_PrimitiveCountNV = uint(primitiveCount);
                }
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec4 vertexColor;
            out vec4 fragColor;
            void main() {
                fragColor = vertexColor;
            }
            """;

    private static int program;
    private static int vertexBuffer;
    private static int metadataBuffer;
    private static int sizesBuffer;
    private static int transformsBuffer;
    private static int boundsBuffer;
    private static int vertexArray;
    private static int mvpLocation;
    private static int playerLocation;
    private static int innerRadiusLocation;
    private static int outerRadiusLocation;
    private static List<FarTerrainRenderer.TileMesh> cachedMeshes = List.of();
    private static List<Meshlet> meshlets = List.of();
    private static int lastDrawnQuads;
    private static String failure = "";
    private static boolean unavailable;

    private FarTerrainGpuRenderer() {}

    static int render(WorldRenderContext context, List<FarTerrainRenderer.TileMesh> meshes,
                      double cameraX, double cameraZ, double playerX, double playerZ,
                      double innerRadius, double outerRadius, int maxQuads) {
        if (meshes.isEmpty() || maxQuads <= 0) return -1;
        if (unavailable || !GL.getCapabilities().GL_NV_mesh_shader) return -1;

        try {
            ensureProgram();
            synchronizeMeshes(meshes, maxQuads);
            if (meshlets.isEmpty()) return 0;

            Minecraft client = Minecraft.getInstance();
            PoseStack.Pose pose = context.matrices().last();
            Matrix4f mvp = new Matrix4f(context.gameRenderer()
                    .getBasicProjectionMatrix(client.options.fov().get()))
                    .mul(pose.pose());

            uploadFrameTransforms(cameraX, cameraZ);
            draw(mvp, playerX - cameraX, playerZ - cameraZ, innerRadius, outerRadius);
            lastDrawnQuads = meshlets.stream().mapToInt(Meshlet::quadCount).sum();
            failure = "";
            return lastDrawnQuads;
        } catch (RuntimeException | LinkageError error) {
            failure = error.getClass().getSimpleName() + ": "
                    + (error.getMessage() == null ? "GPU renderer initialization failed" : error.getMessage());
            unavailable = true;
            release();
            return -1;
        }
    }

    private static void ensureProgram() {
        if (program != 0) return;
        int mesh = compileShader(MESH_SHADER_NV, MESH_SHADER);
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, mesh);
        GL20.glAttachShader(linked, fragment);
        GL20.glLinkProgram(linked);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(linked);
            GL20.glDeleteProgram(linked);
            GL20.glDeleteShader(mesh);
            GL20.glDeleteShader(fragment);
            throw new IllegalStateException("Mesh-shader link failed: " + log);
        }
        GL20.glDeleteShader(mesh);
        GL20.glDeleteShader(fragment);
        program = linked;
        mvpLocation = GL20.glGetUniformLocation(program, "uMvp");
        playerLocation = GL20.glGetUniformLocation(program, "uPlayerXZ");
        innerRadiusLocation = GL20.glGetUniformLocation(program, "uInnerRadius");
        outerRadiusLocation = GL20.glGetUniformLocation(program, "uOuterRadius");

        vertexBuffer = GL15.glGenBuffers();
        metadataBuffer = GL15.glGenBuffers();
        sizesBuffer = GL15.glGenBuffers();
        transformsBuffer = GL15.glGenBuffers();
        boundsBuffer = GL15.glGenBuffers();
        vertexArray = GL30.glGenVertexArrays();
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Mesh shader compilation failed: " + log);
        }
        return shader;
    }

    private static void synchronizeMeshes(List<FarTerrainRenderer.TileMesh> meshes, int maxQuads) {
        boolean unchanged = meshes.size() == cachedMeshes.size();
        if (unchanged) {
            for (int i = 0; i < meshes.size(); i++) {
                if (meshes.get(i) != cachedMeshes.get(i)) {
                    unchanged = false;
                    break;
                }
            }
        }
        if (unchanged) return;

        List<Meshlet> nextMeshlets = new ArrayList<>();
        long totalVertices = 0L;
        int totalQuads = 0;
        for (FarTerrainRenderer.TileMesh mesh : meshes) {
            int resolution = mesh.key().resolution();
            int side = resolution + 1;
            long tileVertices = (long) side * side;
            if ((totalVertices + tileVertices) * 8L > MAX_GPU_BYTES) break;
            int base = (int) totalVertices;
            totalVertices += tileVertices;
            for (int z = 0; z < resolution; z += MAX_MESHLET_CELLS) {
                for (int x = 0; x < resolution; x += MAX_MESHLET_CELLS) {
                    int cellsX = Math.min(MAX_MESHLET_CELLS, resolution - x);
                    int cellsZ = Math.min(MAX_MESHLET_CELLS, resolution - z);
                    int quads = cellsX * cellsZ;
                    if (totalQuads + quads > maxQuads) break;
                    nextMeshlets.add(new Meshlet(mesh, base, side, x, z, cellsX, cellsZ));
                    totalQuads += quads;
                }
                if (totalQuads >= maxQuads) break;
            }
            if (totalQuads >= maxQuads) break;
        }

        if (nextMeshlets.isEmpty()) {
            cachedMeshes = List.copyOf(meshes);
            meshlets = List.of();
            return;
        }

        int vertexCount = (int) totalVertices;
        IntBuffer vertices = MemoryUtil.memAllocInt(vertexCount * 2);
        IntBuffer metadata = MemoryUtil.memAllocInt(nextMeshlets.size() * 4);
        IntBuffer sizes = MemoryUtil.memAllocInt(nextMeshlets.size() * 4);
        try {
            for (FarTerrainRenderer.TileMesh mesh : meshes) {
                if (vertices.position() / 2 >= vertexCount) break;
                for (int i = 0; i < mesh.heights().length; i++) {
                    if (vertices.position() / 2 >= vertexCount) break;
                    vertices.put(Float.floatToRawIntBits((float) mesh.heights()[i]));
                    vertices.put(mesh.colors()[i]);
                }
            }
            for (Meshlet meshlet : nextMeshlets) {
                metadata.put(meshlet.vertexBase()).put(meshlet.side())
                        .put(meshlet.startX()).put(meshlet.startZ());
                sizes.put(meshlet.cellsX()).put(meshlet.cellsZ()).put(0).put(0);
            }
            vertices.flip();
            metadata.flip();
            sizes.flip();

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, vertexBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, metadataBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, metadata, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, sizesBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, sizes, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, transformsBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                    (long) nextMeshlets.size() * 4L * Float.BYTES, GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, boundsBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                    (long) nextMeshlets.size() * 4L * Float.BYTES, GL15.GL_STREAM_DRAW);
        } finally {
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(metadata);
            MemoryUtil.memFree(sizes);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        }
        cachedMeshes = List.copyOf(meshes);
        meshlets = List.copyOf(nextMeshlets);
    }

    private static void uploadFrameTransforms(double cameraX, double cameraZ) {
        FloatBuffer transforms = MemoryUtil.memAllocFloat(meshlets.size() * 4);
        FloatBuffer bounds = MemoryUtil.memAllocFloat(meshlets.size() * 4);
        try {
            for (Meshlet meshlet : meshlets) {
                FarTerrainRenderer.TileMesh mesh = meshlet.mesh();
                double stepX = (double) mesh.spanBlocks() / mesh.key().resolution();
                double stepZ = stepX;
                double originX = mesh.originX();
                double originZ = mesh.originZ();
                double minY = Double.POSITIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                for (int z = meshlet.startZ(); z <= meshlet.startZ() + meshlet.cellsZ(); z++) {
                    for (int x = meshlet.startX(); x <= meshlet.startX() + meshlet.cellsX(); x++) {
                        int height = mesh.heights()[z * meshlet.side() + x];
                        minY = Math.min(minY, height);
                        maxY = Math.max(maxY, height);
                    }
                }
                double centerX = originX + (meshlet.startX() + meshlet.cellsX() * 0.5) * stepX;
                double centerZ = originZ + (meshlet.startZ() + meshlet.cellsZ() * 0.5) * stepZ;
                double centerY = (minY + maxY) * 0.5;
                double halfX = meshlet.cellsX() * stepX * 0.5;
                double halfZ = meshlet.cellsZ() * stepZ * 0.5;
                double halfY = (maxY - minY) * 0.5;
                float radius = (float) Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);

                transforms.put((float) (originX - cameraX))
                        .put((float) (originZ - cameraZ))
                        .put((float) stepX).put((float) stepZ);
                bounds.put((float) (centerX - cameraX)).put((float) centerY)
                        .put((float) (centerZ - cameraZ)).put(radius + 2.0f);
            }
            transforms.flip();
            bounds.flip();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, transformsBuffer);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, transforms);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, boundsBuffer);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, bounds);
        } finally {
            MemoryUtil.memFree(transforms);
            MemoryUtil.memFree(bounds);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        }
    }

    private static void draw(Matrix4f mvp, double playerX, double playerZ,
                             double innerRadius, double outerRadius) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int previousDepthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            mvp.get(matrix);
            GL20.glUseProgram(program);
            GL20.glUniformMatrix4fv(mvpLocation, false, matrix);
            GL20.glUniform2f(playerLocation, (float) playerX, (float) playerZ);
            GL20.glUniform1f(innerRadiusLocation, (float) innerRadius);
            GL20.glUniform1f(outerRadiusLocation, (float) outerRadius);

            GL30.glBindVertexArray(vertexArray);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, vertexBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, metadataBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, sizesBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, transformsBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, boundsBuffer);

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            NVMeshShader.glDrawMeshTasksNV(0, meshlets.size());
        } finally {
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, 0);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, 0);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, 0);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, 0);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousBuffer);
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
            GL11.glDepthFunc(previousDepthFunction);
            GL11.glDepthMask(depthMask);
            setEnabled(GL11.GL_DEPTH_TEST, depthEnabled);
            setEnabled(GL11.GL_BLEND, blendEnabled);
            setEnabled(GL11.GL_CULL_FACE, cullEnabled);
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability);
        else GL11.glDisable(capability);
    }

    static int getLastDrawnQuads() {
        return lastDrawnQuads;
    }

    static String getStatusLine() {
        if (!GL.getCapabilities().GL_NV_mesh_shader) return "NV mesh shaders unavailable; using Minecraft fallback";
        if (unavailable) return "NV mesh shaders failed; using Minecraft fallback: " + failure;
        if (program == 0) return "NV mesh shaders available; backend initializes on first FarView draw";
        return "NV mesh-shader backend active | " + meshlets.size() + " meshlets | "
                + lastDrawnQuads + " source quads submitted";
    }

    static void clearWorld() {
        cachedMeshes = List.of();
        meshlets = List.of();
        lastDrawnQuads = 0;
        if (GL.getCapabilities() != null) {
            releaseBuffers();
        }
    }

    static void shutdown() {
        if (GL.getCapabilities() != null) release();
        unavailable = false;
        failure = "";
        cachedMeshes = List.of();
        meshlets = List.of();
    }

    private static void release() {
        releaseBuffers();
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
        unavailable = false;
    }

    private static void releaseBuffers() {
        if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer);
        if (metadataBuffer != 0) GL15.glDeleteBuffers(metadataBuffer);
        if (sizesBuffer != 0) GL15.glDeleteBuffers(sizesBuffer);
        if (transformsBuffer != 0) GL15.glDeleteBuffers(transformsBuffer);
        if (boundsBuffer != 0) GL15.glDeleteBuffers(boundsBuffer);
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray);
        vertexBuffer = metadataBuffer = sizesBuffer = transformsBuffer = boundsBuffer = vertexArray = 0;
    }

    private record Meshlet(FarTerrainRenderer.TileMesh mesh, int vertexBase, int side,
                           int startX, int startZ, int cellsX, int cellsZ) {
        private int quadCount() {
            return cellsX * cellsZ;
        }
    }
}
