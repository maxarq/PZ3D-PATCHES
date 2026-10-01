package com.max.pz3dfix;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import zombie.core.Color;

import java.nio.FloatBuffer;

public final class FarTreeShader {

    private static final FarTreeShader INSTANCE = new FarTreeShader();
    public static FarTreeShader getInstance() { return INSTANCE; }

    private int program = 0;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    private int uVP = -1;
    private int uMeshOrigin = -1;
    private int uCameraPos = -1;
    private int uSunDir = -1;
    private int uDirectLight = -1;
    private int uAmbientLight = -1;
    private int uFogColor = -1;
    private int uFogDensity = -1;
    private int uRainIntensity = -1;
    private int uTexture = -1;
    private int uCoverageTex = -1;
    private int uPlayerChunk = -1;

    private boolean initialized = false;

    public void init() {
        if (initialized) return;
        initialized = true;

        String vert =
                "#version 330 core\n" +
                        "layout(location=0) in vec3 in_rootPos;\n" +
                        "layout(location=1) in vec2 in_corner;\n" +
                        "layout(location=2) in vec2 in_size;\n" +
                        "layout(location=3) in vec2 in_texCoord;\n" +
                        "layout(location=4) in vec4 in_bounds;\n" +
                        "\n" +
                        "uniform mat4 u_viewProjection;\n" +
                        "uniform vec3 u_meshOrigin;\n" +
                        "uniform vec3 u_cameraPos;\n" +
                        "\n" +
                        "out vec3 v_worldPos;\n" +
                        "out vec3 v_normal;\n" +
                        "out vec2 v_texCoord;\n" +
                        "out vec4 v_bounds;\n" +
                        "out vec2 v_rootTile;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    v_rootTile = in_rootPos.xy;\n" +
                        "    vec3 localRoot = in_rootPos - u_meshOrigin;\n" +
                        "    vec2 toCam = u_cameraPos.xy - localRoot.xy;\n" +
                        "    float d = length(toCam);\n" +
                        "    vec2 fwd = (d > 0.001) ? (toCam / d) : vec2(0.0, 1.0);\n" +
                        "    vec2 right = vec2(-fwd.y, fwd.x);\n" +
                        "\n" +
                        "    vec3 scenePos;\n" +
                        "    scenePos.xy = localRoot.xy + right * (in_corner.x * in_size.x);\n" +
                        "    scenePos.z = localRoot.z + (in_corner.y * in_size.y);\n" +
                        "\n" +
                        "    v_normal = normalize(vec3(fwd * 0.75 + right * (in_corner.x * 0.70), 0.35));\n" +
                        "    v_worldPos = scenePos;\n" +
                        "    v_texCoord = in_texCoord;\n" +
                        "    v_bounds = in_bounds;\n" +
                        "    gl_Position = u_viewProjection * vec4(scenePos, 1.0);\n" +
                        "}\n";

        String frag =
                "#version 330 core\n" +
                        "in vec3 v_worldPos;\n" +
                        "in vec3 v_normal;\n" +
                        "in vec2 v_texCoord;\n" +
                        "in vec4 v_bounds;\n" +
                        "in vec2 v_rootTile;\n" +
                        "\n" +
                        "uniform sampler2D u_texture;\n" +
                        "uniform sampler2D u_pz3dCoverage;\n" +
                        "uniform ivec2 u_playerChunk;\n" +
                        "uniform vec3 u_cameraPos;\n" +
                        "uniform vec3 u_sunDirection;\n" +
                        "uniform vec3 u_directLight;\n" +
                        "uniform vec3 u_ambientLight;\n" +
                        "uniform vec3 u_fogColor;\n" +
                        "uniform float u_fogDensity;\n" +
                        "uniform float u_rainIntensity;\n" +
                        "\n" +
                        "out vec4 fragColor;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    ivec2 chunkCoord = ivec2(floor(v_rootTile / 8.0));\n" +
                        "    ivec2 rel = chunkCoord - u_playerChunk + ivec2(16, 16);\n" +
                        "    if (rel.x >= 0 && rel.x < 32 && rel.y >= 0 && rel.y < 32) {\n" +
                        "        float pz3dCovered = texelFetch(u_pz3dCoverage, rel, 0).r;\n" +
                        "        if (pz3dCovered > 0.5) discard;\n" +
                        "    }\n" +
                        "\n" +
                        "    vec2 texSize = vec2(textureSize(u_texture, 0));\n" +
                        "    vec2 halfPixel = 0.5 / texSize;\n" +
                        "    vec2 uvClamped = clamp(v_texCoord, v_bounds.xy + halfPixel, v_bounds.zw - halfPixel);\n" +
                        "\n" +
                        "    vec4 texColor = texture(u_texture, uvClamped);\n" +
                        "    if (texColor.a < 0.35) discard;\n" +
                        "\n" +
                        "    vec3 normal = normalize(v_normal);\n" +
                        "    float NdotL = max(0.0, dot(normal, u_sunDirection));\n" +
                        "\n" +
                        "    vec3 ambient = u_ambientLight * 1.15;\n" +
                        "    vec3 lit = texColor.rgb * (ambient + (u_directLight * NdotL));\n" +
                        "\n" +
                        "    if (u_rainIntensity > 0.001) {\n" +
                        "        lit *= mix(1.0, 0.72, u_rainIntensity);\n" +
                        "    }\n" +
                        "\n" +
                        "    float dist = length(v_worldPos - u_cameraPos);\n" +
                        "    float opticalDepth = dist * u_fogDensity;\n" +
                        "    float fogFactor = clamp(1.0 - exp(-opticalDepth * opticalDepth), 0.0, 1.0);\n" +
                        "\n" +
                        "    fragColor = vec4(mix(lit, u_fogColor, fogFactor), 1.0);\n" +
                        "}\n";

        this.program = compileProgram(vert, frag);
        cacheUniforms();
    }

    private int compileProgram(String vert, String frag) {
        int vShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vShader, vert);
        GL20.glCompileShader(vShader);
        if (GL20.glGetShaderi(vShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[PZ3DFix] FarTreeShader vert compile failed:\n" + GL20.glGetShaderInfoLog(vShader, 2048));
        }

        int fShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fShader, frag);
        GL20.glCompileShader(fShader);
        if (GL20.glGetShaderi(fShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[PZ3DFix] FarTreeShader frag compile failed:\n" + GL20.glGetShaderInfoLog(fShader, 2048));
        }

        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vShader);
        GL20.glAttachShader(prog, fShader);
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("[PZ3DFix] FarTreeShader link failed:\n" + GL20.glGetProgramInfoLog(prog, 2048));
        }

        GL20.glDeleteShader(vShader);
        GL20.glDeleteShader(fShader);
        return prog;
    }

    private void cacheUniforms() {
        uVP = GL20.glGetUniformLocation(program, "u_viewProjection");
        uMeshOrigin = GL20.glGetUniformLocation(program, "u_meshOrigin");
        uCameraPos = GL20.glGetUniformLocation(program, "u_cameraPos");
        uSunDir = GL20.glGetUniformLocation(program, "u_sunDirection");
        uDirectLight = GL20.glGetUniformLocation(program, "u_directLight");
        uAmbientLight = GL20.glGetUniformLocation(program, "u_ambientLight");
        uFogColor = GL20.glGetUniformLocation(program, "u_fogColor");
        uFogDensity = GL20.glGetUniformLocation(program, "u_fogDensity");
        uRainIntensity = GL20.glGetUniformLocation(program, "u_rainIntensity");
        uTexture = GL20.glGetUniformLocation(program, "u_texture");
        uCoverageTex = GL20.glGetUniformLocation(program, "u_pz3dCoverage");
        uPlayerChunk = GL20.glGetUniformLocation(program, "u_playerChunk");
    }

    public void bind() {
        init();
        GL20.glUseProgram(program);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void setViewProjection(Matrix4f vp) {
        matrixBuffer.clear();
        vp.get(matrixBuffer);
        if (uVP >= 0) GL20.glUniformMatrix4fv(uVP, false, matrixBuffer);
    }

    public void setMeshOrigin(float ox, float oy, float oz) {
        if (uMeshOrigin >= 0) GL20.glUniform3f(uMeshOrigin, ox, oy, oz);
    }

    public void setCameraPos(float cx, float cy, float cz) {
        if (uCameraPos >= 0) GL20.glUniform3f(uCameraPos, cx, cy, cz);
    }

    public void setSunDirection(float dx, float dy, float dz) {
        if (uSunDir >= 0) GL20.glUniform3f(uSunDir, dx, dy, dz);
    }

    public void setLighting(float dirR, float dirG, float dirB, float ambR, float ambG, float ambB) {
        if (uDirectLight >= 0) GL20.glUniform3f(uDirectLight, dirR, dirG, dirB);
        if (uAmbientLight >= 0) GL20.glUniform3f(uAmbientLight, ambR, ambG, ambB);
    }

    public void setFog(Color color, float density) {
        if (uFogColor >= 0) GL20.glUniform3f(uFogColor, color.r, color.g, color.b);
        if (uFogDensity >= 0) GL20.glUniform1f(uFogDensity, density);
    }

    public void setRainIntensity(float rain) {
        if (uRainIntensity >= 0) GL20.glUniform1f(uRainIntensity, rain);
    }

    public void setPlayerChunk(int chunkX, int chunkY) {
        if (uPlayerChunk >= 0) GL20.glUniform2i(uPlayerChunk, chunkX, chunkY);
    }

    public void setTextureUnits(int tileUnit, int coverageUnit) {
        if (uTexture >= 0) GL20.glUniform1i(uTexture, tileUnit);
        if (uCoverageTex >= 0) GL20.glUniform1i(uCoverageTex, coverageUnit);
    }
}