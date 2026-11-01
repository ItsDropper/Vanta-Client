package name.vantaclient.client.util;

import name.vantaclient.client.config.DropperUtilsConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Temporal camera-motion blur; rendered after the world so HUD/GUI remain sharp. */
public final class MotionBlurRenderer {
    private static int program, vao, vbo, currentTexture, historyTexture, widthCache, heightCache;
    private static int currentFrameUniform, historyFrameUniform, motionUniform, strengthUniform, samplesUniform;
    private static boolean failed, rotationValid, historyValid;
    private static float previousYaw, previousPitch, previousPlayerYaw, previousPlayerPitch;
    private static float accumulatedYaw, accumulatedPitch;
    private static boolean playerRotationValid;
    private static long previousFrameNanos;
    private static Object previousLevel, previousPlayer;
    private static double previousX, previousY, previousZ;
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(4);
    private static final IntBuffer SCISSOR_BUFFER = BufferUtils.createIntBuffer(4);
    private static final ByteBuffer COLOR_MASK_BUFFER = BufferUtils.createByteBuffer(4);

    private static final String VERTEX = """
            #version 150
            in vec2 Position;
            in vec2 TexCoord;
            out vec2 uv;
            void main() { gl_Position = vec4(Position, 0.0, 1.0); uv = TexCoord; }
            """;
    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D CurrentFrame;
            uniform sampler2D HistoryFrame;

            uniform vec2 Motion;
            uniform float Strength;
            uniform int Samples;
            in vec2 uv;
            out vec4 color;
            void main() {
                vec4 current = texture(CurrentFrame, uv);
                vec3 trail = vec3(0.0);
                int count = clamp(Samples, 2, 12);
                for (int i = 0; i < 12; ++i) {
                    if (i >= count) break;
                    float t = float(i + 1) / float(count);
                    vec2 p = clamp(uv + Motion * t, vec2(0.001), vec2(0.999));
                    trail += texture(HistoryFrame, p).rgb;
                }
                trail /= float(count);
                color = vec4(mix(current.rgb, trail, Strength), current.a);
            }
            """;

    private MotionBlurRenderer() {}

    public static void reset() {

        rotationValid = false;
        playerRotationValid = false;
        historyValid = false;
        accumulatedYaw = 0.0f;
        accumulatedPitch = 0.0f;
        previousFrameNanos = 0L;
    }

    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        DropperUtilsConfig config = DropperUtilsConfig.get();
        if (failed || !config.motionBlur || mc.player == null || mc.screen != null
                || mc.getWindow().getWidth() <= 0 || mc.getWindow().getHeight() <= 0) {
            reset();
            return;
        }

        // Read the rendered camera rather than the player entity. The camera can
        // move between entity ticks, so player rotation misses fast mouse input.
        float yaw = mc.gameRenderer.getMainCamera().yRot();
        float pitch = mc.gameRenderer.getMainCamera().xRot();
        float playerYaw = mc.player.getYRot();
        float playerPitch = mc.player.getXRot();
        long now = System.nanoTime();
        double playerX = mc.player.getX(), playerY = mc.player.getY(), playerZ = mc.player.getZ();
        double dx = playerX - previousX, dy = playerY - previousY, dz = playerZ - previousZ;
        boolean cameraCut = (previousLevel != null && previousLevel != mc.level)
                || (previousPlayer != null && previousPlayer != mc.player)
                || (rotationValid && dx * dx + dy * dy + dz * dz > 256.0)
                || (previousFrameNanos != 0L && now - previousFrameNanos > 250_000_000L);
        previousLevel = mc.level;
        previousPlayer = mc.player;
        previousX = playerX;
        previousY = playerY;
        previousZ = playerZ;
        if (cameraCut) reset();

        if (!rotationValid || !playerRotationValid) {
            previousYaw = yaw;
            previousPitch = pitch;
            previousPlayerYaw = playerYaw;
            previousPlayerPitch = playerPitch;
            previousFrameNanos = now;
            rotationValid = true;
            playerRotationValid = true;
            return;
        }

        float cameraYawDelta = wrap(yaw - previousYaw);
        float cameraPitchDelta = pitch - previousPitch;
        float entityYawDelta = wrap(playerYaw - previousPlayerYaw);
        float entityPitchDelta = playerPitch - previousPlayerPitch;
        previousYaw = yaw;
        previousPitch = pitch;
        previousPlayerYaw = playerYaw;
        previousPlayerPitch = playerPitch;

        // Some render paths expose a camera that is updated later in the frame.
        // Fall back to the player view rotation when it reports more motion.
        float cameraMotion = cameraYawDelta * cameraYawDelta + cameraPitchDelta * cameraPitchDelta;
        float entityMotion = entityYawDelta * entityYawDelta + entityPitchDelta * entityPitchDelta;
        float dyaw = entityMotion > cameraMotion ? entityYawDelta : cameraYawDelta;
        float dpitch = entityMotion > cameraMotion ? entityPitchDelta : cameraPitchDelta;

        // Keep a short exposure window with time-based decay. A fixed per-frame
        // multiplier made the effect vanish much faster at high frame rates.
        float elapsed = clamp((now - previousFrameNanos) / 1_000_000_000.0f, 0.0f, 0.1f);
        previousFrameNanos = now;
        float decay = (float) Math.pow(0.72, elapsed * 60.0f);
        float frameScale = elapsed * 60.0f;
        accumulatedYaw = accumulatedYaw * decay + dyaw * frameScale;
        accumulatedPitch = accumulatedPitch * decay + dpitch * frameScale;

        float sensitivity = clamp(config.motionBlurSensitivity, 0.25f, 2.0f);
        float motionYaw = accumulatedYaw * sensitivity;
        float motionPitch = accumulatedPitch * sensitivity;
        float movement = (float) Math.sqrt(motionYaw * motionYaw + motionPitch * motionPitch);


        VIEWPORT_BUFFER.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
        int vx=VIEWPORT_BUFFER.get(0), vy=VIEWPORT_BUFFER.get(1), vw=VIEWPORT_BUFFER.get(2), vh=VIEWPORT_BUFFER.get(3);
        if (vw <= 0 || vh <= 0) return;
        int w = vw, h = vh;
        SCISSOR_BUFFER.clear();
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, SCISSOR_BUFFER);
        int sx=SCISSOR_BUFFER.get(0), sy=SCISSOR_BUFFER.get(1);
        int sw=SCISSOR_BUFFER.get(2), sh=SCISSOR_BUFFER.get(3);
        COLOR_MASK_BUFFER.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, COLOR_MASK_BUFFER);
        boolean colorRed=COLOR_MASK_BUFFER.get(0)!=0, colorGreen=COLOR_MASK_BUFFER.get(1)!=0;
        boolean colorBlue=COLOR_MASK_BUFFER.get(2)!=0, colorAlpha=COLOR_MASK_BUFFER.get(3)!=0;
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArrayBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTex0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int oldTex1=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try {
            ensure(w,h);
            if (failed) return;

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,currentTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,vx,vy,w,h);
            // The previous frame contains scene positions from before the camera
            // moved: yaw-right shifts the old image right; pitch-down shifts it down.
            float mx = clamp(motionYaw / 60.0f, -0.16f, 0.16f);
            float my = clamp(-motionPitch / 45.0f, -0.16f, 0.16f);
            float strength = historyValid
                    ? clamp(config.motionBlurStrength, 0.0f, 0.85f)
                      * clamp(movement / 0.02f, 0.0f, 1.0f)
                    : 0.0f;

            GL11.glViewport(vx,vy,w,h);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,currentTexture);
            GL20.glUniform1i(currentFrameUniform,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,historyTexture);
            GL20.glUniform1i(historyFrameUniform,1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL20.glUniform2f(motionUniform,mx,my);
            GL20.glUniform1f(strengthUniform,strength);
            GL20.glUniform1i(samplesUniform,config.motionBlurSamples);
            GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP,0,4);

            int swap = historyTexture;
            historyTexture = currentTexture;
            currentTexture = swap;
            historyValid = true;
        } catch (Throwable t) {
            failed=true;
            
            System.err.println("[DropperUtils] Motion blur disabled after a rendering error.");
            t.printStackTrace();
        } finally {
            GL20.glUseProgram(oldProgram);
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArrayBuffer);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTex0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTex1);
            GL13.glActiveTexture(oldActive);
            GL11.glViewport(vx,vy,vw,vh);
            GL11.glScissor(sx,sy,sw,sh);
            GL11.glColorMask(colorRed,colorGreen,colorBlue,colorAlpha);
            if(depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if(blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if(scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if(stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            if(cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }

    private static void ensure(int w,int h) {
        if(program==0) {
            program=link(VERTEX,FRAGMENT);
            if(program==0) { failed=true; return; }
            currentFrameUniform=GL20.glGetUniformLocation(program,"CurrentFrame");
            historyFrameUniform=GL20.glGetUniformLocation(program,"HistoryFrame");
            motionUniform=GL20.glGetUniformLocation(program,"Motion");
            strengthUniform=GL20.glGetUniformLocation(program,"Strength");
            samplesUniform=GL20.glGetUniformLocation(program,"Samples");
            if(currentFrameUniform<0 || historyFrameUniform<0 || motionUniform<0
                    || strengthUniform<0 || samplesUniform<0) {
                GL20.glDeleteProgram(program);
                program=0;
                failed=true;
                System.err.println("[Vanta Client] Motion blur shader is missing a required uniform.");
                return;
            }
            vao=GL30.glGenVertexArrays();
            vbo=GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);
            float[] vertices={-1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,1,1,1};
            FloatBuffer data=BufferUtils.createFloatBuffer(vertices.length);
            data.put(vertices).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,16,0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,16,8L);
        }
        if(currentTexture==0) currentTexture=GL11.glGenTextures();
        if(historyTexture==0) historyTexture=GL11.glGenTextures();
        if(w!=widthCache || h!=heightCache) {
            allocate(currentTexture,w,h);
            allocate(historyTexture,w,h);
            historyValid = false;
            widthCache=w; heightCache=h; 
        }
    }

    private static void allocate(int tex,int w,int h) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,0x812F);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null);
    }

    private static int link(String vs,String fs) {
        int v=compile(GL20.GL_VERTEX_SHADER,vs), f=compile(GL20.GL_FRAGMENT_SHADER,fs);
        if(v==0 || f==0) return 0;
        int p=GL20.glCreateProgram();
        GL20.glAttachShader(p,v); GL20.glAttachShader(p,f);
        GL20.glBindAttribLocation(p,0,"Position"); GL20.glBindAttribLocation(p,1,"TexCoord");
        GL20.glLinkProgram(p); GL20.glDeleteShader(v); GL20.glDeleteShader(f);
        if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==GL11.GL_FALSE) {
            System.err.println("[DropperUtils] Motion blur shader link failed: "+GL20.glGetProgramInfoLog(p));
            GL20.glDeleteProgram(p); return 0;
        }
        return p;
    }

    private static int compile(int type,String src) {
        int s=GL20.glCreateShader(type);
        GL20.glShaderSource(s,src); GL20.glCompileShader(s);
        if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE) {
            System.err.println("[DropperUtils] Motion blur shader compile failed: "+GL20.glGetShaderInfoLog(s));
            GL20.glDeleteShader(s); return 0;
        }
        return s;
    }

    private static float wrap(float v) {
        while(v>=180) v-=360;
        while(v< -180) v+=360;
        return v;
    }
    private static float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
}
