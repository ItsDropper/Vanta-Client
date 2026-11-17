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
 * Optional NV mesh-shader submission for greedy 3D voxel meshes.
 * Each GPU mesh task owns up to 32 quads and performs frustum plus radial-band
 * culling before emitting triangles. The CPU uploads immutable chunk meshes once
 * per cache revision; per-frame work is limited to transforms and one task draw.
 * This backend has no Nvidium dependency and falls back to the VertexConsumer path.
 */
final class FarTerrainVoxelGpuRenderer {
    private static final int MESH_SHADER_NV = 0x9559;
    private static final int QUADS_PER_MESHLET = 32;
    private static final int MAX_UPLOAD_QUADS = 2_097_120;
    private static final int MAX_GPU_BYTES = 96 * 1024 * 1024;

    private static final String MESH_SHADER = """
            #version 450 core
            #extension GL_NV_mesh_shader : require

            layout(local_size_x = 32) in;
            layout(triangles, max_vertices = 128, max_primitives = 64) out;

            layout(std430, binding = 0) readonly buffer VoxelVertices {
                uvec2 voxelVertices[];
            };
            layout(std430, binding = 1) readonly buffer VoxelMeshletMetadata {
                ivec4 meshletMetadata[];
            };
            layout(std430, binding = 2) readonly buffer VoxelMeshletTransforms {
                vec4 meshletTransforms[];
            };
            layout(std430, binding = 3) readonly buffer VoxelMeshletBounds {
                vec4 meshletBounds[];
            };

            uniform mat4 uMvp;
            uniform vec2 uPlayerXZ;
            uniform float uInnerRadius;
            uniform float uOuterRadius;

            pervertexNV out vec4 vertexColor[];

            bool outsidePlane(vec4 plane, vec3 center, float radius) {
                return dot(plane, vec4(center, 1.0)) < -radius * length(plane.xyz);
            }

            void main() {
                uint meshlet = gl_WorkGroupID.x;
                ivec4 meta = meshletMetadata[meshlet];
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

                int quadCount = meta.y;
                for (int quad = int(gl_LocalInvocationIndex); quad < quadCount;
                     quad += int(gl_WorkGroupSize.x)) {
                    uint firstVertex = uint(meta.x + quad * 4);
                    uint outputVertex = uint(quad * 4);
                    for (uint corner = 0u; corner < 4u; corner++) {
                        uvec2 packedVertex = voxelVertices[firstVertex + corner];
                        uint packedPosition = packedVertex.x;
                        float localX = float(packedPosition & 31u);
                        float localZ = float((packedPosition >> 5u) & 31u);
                        int worldY = int(packedPosition << 6u) >> 16;
                        vec3 position = vec3(
                            transform.x + localX,
                            transform.z + float(worldY),
                            transform.y + localZ
                        );
                        gl_MeshVerticesNV[outputVertex + corner].gl_Position =
                            uMvp * vec4(position, 1.0);
                        uint color = packedVertex.y;
                        vertexColor[outputVertex + corner] = vec4(
                            float((color >> 16u) & 255u),
                            float((color >> 8u) & 255u),
                            float(color & 255u),
                            255.0
                        ) / 255.0;
                    }
                    uint primitive = uint(quad * 2);
                    gl_PrimitiveIndicesNV[primitive * 3u] = outputVertex;
                    gl_PrimitiveIndicesNV[primitive * 3u + 1u] = outputVertex + 1u;
                    gl_PrimitiveIndicesNV[primitive * 3u + 2u] = outputVertex + 2u;
                    gl_PrimitiveIndicesNV[(primitive + 1u) * 3u] = outputVertex;
                    gl_PrimitiveIndicesNV[(primitive + 1u) * 3u + 1u] = outputVertex + 2u;
                    gl_PrimitiveIndicesNV[(primitive + 1u) * 3u + 2u] = outputVertex + 3u;
                }
                barrier();
                if (gl_LocalInvocationIndex == 0u) {
                    gl_PrimitiveCountNV = uint(quadCount * 2);
                }
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 450 core
            in vec4 vertexColor;
            out vec4 fragColor;
            void main() {
                fragColor = vertexColor;
            }
            """;

    private static int program;
    private static int vertexBuffer;
    private static int metadataBuffer;
    private static int transformsBuffer;
    private static int boundsBuffer;
    private static int vertexArray;
    private static int mvpLocation;
    private static int playerLocation;
    private static int innerRadiusLocation;
    private static int outerRadiusLocation;
    private static List<FarTerrainVoxelMesh> cachedMeshes = List.of();
    private static List<Meshlet> meshlets = List.of();
    private static List<Meshlet> activeMeshlets = List.of();
    private static int activeMeshletCount;
    private static int activeQuadCount;
    private static int lastDrawnQuads;
    private static String failure = "";
    private static boolean unavailable;

    private FarTerrainVoxelGpuRenderer() {}

    static int render(WorldRenderContext context, List<FarTerrainVoxelMesh> meshes,
                      double cameraX, double cameraY, double cameraZ,
                      double playerX, double playerZ,
                      double innerRadius, double outerRadius, int maxQuads) {
        if (meshes.isEmpty() || maxQuads <= 0) return 0;
        if (unavailable || !isSupported()) return -1;
        try {
            ensureProgram();
            synchronizeMeshes(meshes);
            selectMeshlets(maxQuads, playerX, playerZ);
            if (activeMeshletCount == 0) return meshlets.isEmpty() ? 0 : -1;

            Minecraft client = Minecraft.getInstance();
            PoseStack.Pose pose = context.matrices().last();
            Matrix4f mvp = new Matrix4f(context.gameRenderer()
                    .getProjectionMatrix(client.options.fov().get())).mul(pose.pose());
            uploadFrameData(cameraX, cameraY, cameraZ);
            draw(mvp, playerX - cameraX, playerZ - cameraZ, innerRadius, outerRadius, activeMeshletCount);
            lastDrawnQuads = activeQuadCount;
            failure = "";
            return lastDrawnQuads;
        } catch (RuntimeException | LinkageError error) {
            failure = error.getClass().getSimpleName() + ": "
                    + (error.getMessage() == null ? "Voxel GPU renderer initialization failed" : error.getMessage());
            unavailable = true;
            release();
            unavailable = true;
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
            throw new IllegalStateException("Voxel mesh-shader link failed: " + log);
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
            throw new IllegalStateException("Voxel shader compilation failed: " + log);
        }
        return shader;
    }

    private static void synchronizeMeshes(List<FarTerrainVoxelMesh> meshes) {
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
        int totalQuads = 0;
        long totalBytes = 0L;
        for (FarTerrainVoxelMesh mesh : meshes) {
            int count = Math.min(mesh.quadCount(), MAX_UPLOAD_QUADS - totalQuads);
            if (count <= 0) break;
            long meshBytes = (long) count * 4L * 2L * Integer.BYTES;
            if (totalBytes + meshBytes > MAX_GPU_BYTES) break;
            float[] positions = mesh.positions();
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (int i = 1; i < count * 12; i += 3) {
                minY = Math.min(minY, positions[i]);
                maxY = Math.max(maxY, positions[i]);
            }
            int vertexBase = totalQuads * 4;
            for (int start = 0; start < count; start += QUADS_PER_MESHLET) {
                int meshletQuads = Math.min(QUADS_PER_MESHLET, count - start);
                nextMeshlets.add(new Meshlet(mesh, vertexBase + start * 4, meshletQuads, minY, maxY));
            }
            totalQuads += count;
            totalBytes += meshBytes;
            if (totalQuads >= MAX_UPLOAD_QUADS) break;
        }

        if (nextMeshlets.isEmpty()) {
            cachedMeshes = List.copyOf(meshes);
            meshlets = List.of();
            activeMeshletCount = 0;
            activeQuadCount = 0;
            return;
        }

        IntBuffer vertices = MemoryUtil.memAllocInt(totalQuads * 4 * 2);
        IntBuffer metadata = MemoryUtil.memAllocInt(nextMeshlets.size() * 4);
        int previousBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            int baseVertex = 0;
            int remainingQuads = totalQuads;
            for (FarTerrainVoxelMesh mesh : meshes) {
                if (remainingQuads <= 0) break;
                float[] positions = mesh.positions();
                int[] colors = mesh.colors();
                int count = Math.min(mesh.quadCount(), remainingQuads);
                for (int quad = 0; quad < count; quad++) {
                    int offset = quad * 12;
                    for (int corner = 0; corner < 4; corner++) {
                        int positionOffset = offset + corner * 3;
                        vertices.put(packPosition(positions[positionOffset],
                                positions[positionOffset + 1], positions[positionOffset + 2]));
                        vertices.put(colors[quad]);
                    }
                }
                baseVertex += count * 4;
                remainingQuads -= count;
            }
            for (Meshlet meshlet : nextMeshlets) {
                metadata.put(meshlet.vertexBase()).put(meshlet.quadCount()).put(0).put(0);
            }
            vertices.flip();
            metadata.flip();

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, vertexBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, metadataBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, metadata, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, transformsBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                    (long) nextMeshlets.size() * 4L * Float.BYTES, GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, boundsBuffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
                    (long) nextMeshlets.size() * 4L * Float.BYTES, GL15.GL_STREAM_DRAW);
        } finally {
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(metadata);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousBuffer);
        }
        cachedMeshes = List.copyOf(meshes);
        meshlets = List.copyOf(nextMeshlets);
        activeMeshlets = List.of();
        activeMeshletCount = 0;
        activeQuadCount = 0;
    }

    static int packPosition(float x, float worldY, float z) {
        int blockX = Math.round(x);
        int blockY = Math.round(worldY);
        int blockZ = Math.round(z);
        if (blockX < 0 || blockX > 31 || blockZ < 0 || blockZ > 31
                || blockY < Short.MIN_VALUE || blockY > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Voxel vertex is outside the packed coordinate range");
        }
        return (blockX & 31) | ((blockZ & 31) << 5) | ((blockY & 0xFFFF) << 10);
    }

    static int unpackX(int packedPosition) {
        return packedPosition & 31;
    }

    static int unpackZ(int packedPosition) {
        return (packedPosition >>> 5) & 31;
    }

    static int unpackY(int packedPosition) {
        return (short) (packedPosition >>> 10);
    }

    private static void selectMeshlets(int maxQuads, double playerX, double playerZ) {
        activeMeshletCount = 0;
        activeQuadCount = 0;
        List<Meshlet> nearest = new ArrayList<>(meshlets);
        nearest.sort(Comparator.comparingDouble(meshlet -> {
            double dx = meshlet.mesh().chunkX() * 16.0 + 8.0 - playerX;
            double dz = meshlet.mesh().chunkZ() * 16.0 + 8.0 - playerZ;
            return dx * dx + dz * dz;
        }));
        List<Meshlet> selected = new ArrayList<>();
        for (Meshlet meshlet : nearest) {
            if (activeQuadCount + meshlet.quadCount() > maxQuads) continue;
            selected.add(meshlet);
            activeQuadCount += meshlet.quadCount();
        }
        activeMeshlets = List.copyOf(selected);
        activeMeshletCount = activeMeshlets.size();
    }

    private static void uploadFrameData(double cameraX, double cameraY, double cameraZ) {
        IntBuffer metadata = MemoryUtil.memAllocInt(activeMeshletCount * 4);
        FloatBuffer transforms = MemoryUtil.memAllocFloat(activeMeshletCount * 4);
        FloatBuffer bounds = MemoryUtil.memAllocFloat(activeMeshletCount * 4);
        int previousBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            for (int meshletIndex = 0; meshletIndex < activeMeshletCount; meshletIndex++) {
                Meshlet meshlet = activeMeshlets.get(meshletIndex);
                metadata.put(meshlet.vertexBase()).put(meshlet.quadCount()).put(0).put(0);
                double originX = meshlet.mesh().chunkX() * 16.0;
                double originZ = meshlet.mesh().chunkZ() * 16.0;
                double centerX = originX + 8.0;
                double centerZ = originZ + 8.0;
                double centerY = (meshlet.minY() + meshlet.maxY()) * 0.5;
                double halfY = (meshlet.maxY() - meshlet.minY()) * 0.5;
                float radius = (float) Math.sqrt(8.0 * 8.0 + 8.0 * 8.0 + halfY * halfY) + 2.0f;
                transforms.put((float) (originX - cameraX))
                        .put((float) (originZ - cameraZ))
                        .put((float) -cameraY).put(0.0f);
                bounds.put((float) (centerX - cameraX))
                        .put((float) (centerY - cameraY))
                        .put((float) (centerZ - cameraZ)).put(radius);
            }
            metadata.flip();
            transforms.flip();
            bounds.flip();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, metadataBuffer);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, metadata);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, transformsBuffer);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, transforms);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, boundsBuffer);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, bounds);
        } finally {
            MemoryUtil.memFree(metadata);
            MemoryUtil.memFree(transforms);
            MemoryUtil.memFree(bounds);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousBuffer);
        }
    }

    private static void draw(Matrix4f mvp, double playerX, double playerZ,
                             double innerRadius, double outerRadius, int meshletCount) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int[] previousIndexed = new int[4];
        for (int binding = 0; binding < previousIndexed.length; binding++) {
            previousIndexed[binding] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, binding);
        }
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
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, transformsBuffer);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, boundsBuffer);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            NVMeshShader.glDrawMeshTasksNV(0, meshletCount);
        } finally {
            for (int binding = 0; binding < previousIndexed.length; binding++) {
                GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, previousIndexed[binding]);
            }
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

    static String getStatusLine() {
        if (!isSupported()) return "NV voxel mesh shaders unavailable; using CPU fallback";
        if (unavailable) return "NV voxel mesh shaders failed; using CPU fallback: " + failure;
        return program == 0 ? "NV voxel mesh shader available"
                : "NV voxel mesh shader active | " + meshlets.size() + " meshlets | " + lastDrawnQuads + " quads";
    }

    static void clearWorld() {
        cachedMeshes = List.of();
        meshlets = List.of();
        activeMeshlets = List.of();
        activeMeshletCount = 0;
        activeQuadCount = 0;
        lastDrawnQuads = 0;
        // Keep the program and buffer objects alive across world changes. The
        // next mesh-list revision replaces their contents without recompiling.
    }

    static void shutdown() {
        if (hasCurrentContext()) release();
        unavailable = false;
        failure = "";
        cachedMeshes = List.of();
        meshlets = List.of();
        activeMeshletCount = 0;
        activeQuadCount = 0;
    }

    private static void release() {
        releaseBuffers();
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
    }

    private static void releaseBuffers() {
        if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer);
        if (metadataBuffer != 0) GL15.glDeleteBuffers(metadataBuffer);
        if (transformsBuffer != 0) GL15.glDeleteBuffers(transformsBuffer);
        if (boundsBuffer != 0) GL15.glDeleteBuffers(boundsBuffer);
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray);
        vertexBuffer = metadataBuffer = transformsBuffer = boundsBuffer = vertexArray = 0;
    }

    private static boolean isSupported() {
        try {
            return GL.getCapabilities().GL_NV_mesh_shader;
        } catch (IllegalStateException noCurrentContext) {
            return false;
        }
    }

    private static boolean hasCurrentContext() {
        try {
            GL.getCapabilities();
            return true;
        } catch (IllegalStateException noCurrentContext) {
            return false;
        }
    }

    private record Meshlet(FarTerrainVoxelMesh mesh, int vertexBase, int quadCount,
                           float minY, float maxY) {}
}
