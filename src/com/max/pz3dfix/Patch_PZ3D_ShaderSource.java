package com.max.pz3dfix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "com.pavelvoronin.pz3d.Renderer", methodName = "source")
public class Patch_PZ3D_ShaderSource {

    @Patch.OnExit
    public static void exit(
            @Patch.Argument(0) String shaderName,
            @Patch.Return(readOnly = false) String glsl
    ) {
        if (glsl == null) return;

        // Shared 3D Volumetric Rain Core
        String sharedVolumetricRain =
                "// Fast 3D spatial hash\n" +
                        "vec4 rainHash3D(vec3 p) {\n" +
                        "    vec4 p4 = fract(vec4(p.xyzx) * vec4(443.897, 441.423, 437.195, 444.129));\n" +
                        "    p4 += dot(p4, p4.wzxy + 19.19);\n" +
                        "    return fract((p4.xxyz + p4.yzzw) * p4.zywx);\n" +
                        "}\n" +
                        "\n" +
                        "// Samples a single volumetric depth shell in true 3D world space\n" +
                        "float sampleVolumetricShell(vec3 origin, vec3 ray, float D, float maxDist, float time, float cellSize, float cellHeight, float dropWidth, float dropLen, float density) {\n" +
                        "    if (density <= 0.001) return 0.0;\n" +
                        "\n" +
                        "    vec3 P = origin + ray * D;\n" +
                        "    float rHoriz = length(ray.xy);\n" +
                        "\n" +
                        "    vec2 cellXY = floor(P.xy / cellSize);\n" +
                        "    vec2 fractXY = fract(P.xy / cellSize);\n" +
                        "    vec2 stepDir = sign(fractXY - 0.5);\n" +
                        "\n" +
                        "    float streak = 0.0;\n" +
                        "\n" +
                        "    // Evaluate local 3D neighborhood so drops cluster naturally\n" +
                        "    for (int i = 0; i < 3; i++) {\n" +
                        "        vec2 offset = (i == 0) ? vec2(0.0) : (i == 1) ? vec2(stepDir.x, 0.0) : vec2(0.0, stepDir.y);\n" +
                        "        vec2 curCell = cellXY + offset;\n" +
                        "\n" +
                        "        // Asynchronous column phase offset\n" +
                        "        vec4 hCol = rainHash3D(vec3(curCell, 13.37));\n" +
                        "        vec2 dropPos = (curCell + 0.12 + 0.76 * hCol.xy) * cellSize;\n" +
                        "        vec2 delta = dropPos - P.xy;\n" +
                        "\n" +
                        "        // Exact longitudinal distance along the ray to the drop column\n" +
                        "        float ds = dot(delta, ray.xy) / max(rHoriz * rHoriz, 0.04);\n" +
                        "        float s = D + clamp(ds, -cellSize, cellSize);\n" +
                        "\n" +
                        "        // Must remain in front of the camera\n" +
                        "        if (s < 0.35) continue;\n" +
                        "\n" +
                        "        // Surface proximity cutoff: NEVER render behind or directly on surfaces!\n" +
                        "        // Fades drops out smoothly within 0.65m of ground or walls so drops never crawl on terrain.\n" +
                        "        float surfaceDist = maxDist - s;\n" +
                        "        if (surfaceDist <= 0.05) continue;\n" +
                        "        float surfaceFade = smoothstep(0.05, 0.65, surfaceDist);\n" +
                        "\n" +
                        "        // Perpendicular distance from ray to vertical drop line\n" +
                        "        float dHoriz = (rHoriz < 0.15) ? length(delta) : abs(delta.x * ray.y - delta.y * ray.x) / rHoriz;\n" +
                        "        float effWidth = dropWidth * (0.80 + 0.40 * hCol.z);\n" +
                        "        if (dHoriz > effWidth) continue;\n" +
                        "\n" +
                        "        float xProfile = 1.0 - (dHoriz / effWidth);\n" +
                        "        xProfile = xProfile * xProfile;\n" +
                        "\n" +
                        "        // True 3D position in the air at the drop\n" +
                        "        vec3 posAtDrop = origin + ray * s;\n" +
                        "        if (!isPointOutdoors(posAtDrop)) continue;\n" +
                        "        float rayZ = posAtDrop.z;\n" +
                        "\n" +
                        "        // Falling animation\n" +
                        "        float colTime = time + hCol.w * 10.0;\n" +
                        "        float tau = rayZ + colTime * 7.5;\n" +
                        "        float cellZ = floor(tau / cellHeight);\n" +
                        "        float fractZ = fract(tau / cellHeight);\n" +
                        "\n" +
                        "        // Non-repeating spacetime seed per cycle\n" +
                        "        vec4 h = rainHash3D(vec3(curCell, cellZ));\n" +
                        "        if (h.w > density) continue;\n" +
                        "\n" +
                        "        // Looking down foreshortens streaks to droplets instead of stretching into lines\n" +
                        "        float effLen = dropLen * (0.75 + 0.45 * h.x) * max(rHoriz, 0.18);\n" +
                        "        float dropZ = h.y * (1.0 - effLen);\n" +
                        "        float dZ = fractZ - dropZ;\n" +
                        "\n" +
                        "        if (dZ > 0.0 && dZ < effLen) {\n" +
                        "            float head = smoothstep(0.0, 0.03, dZ);\n" +
                        "            float tail = smoothstep(effLen, 0.03, dZ);\n" +
                        "            streak += xProfile * head * tail * surfaceFade * (0.65 + 0.35 * h.z);\n" +
                        "        }\n" +
                        "    }\n" +
                        "    return streak;\n" +
                        "}\n" +
                        "\n" +
                        "// 4-Step Jittered Volumetric Rainmarch\n" +
                        "float calculateVolumetricRain(vec3 origin, vec3 ray, float maxDist, float time, float intensity, bool cameraInside) {\n" +
                        "    if (intensity <= 0.001) return 0.0;\n" +
                        "\n" +
                        "    // Screen-space blue-noise dithering to eliminate depth shell stepping\n" +
                        "    float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);\n" +
                        "\n" +
                        "    float d0 = clamp(0.12 + intensity * 0.76, 0.0, 0.88);\n" +
                        "    float d1 = clamp(0.10 + intensity * 0.72, 0.0, 0.82);\n" +
                        "    float d2 = clamp(0.07 + intensity * 0.68, 0.0, 0.75);\n" +
                        "    float d3 = clamp(0.05 + intensity * 0.65, 0.0, 0.70);\n" +
                        "\n" +
                        "    // Shell 0: Immediate Foreground (1.1m)\n" +
                        "    float s0 = 0.0;\n" +
                        "    if (!cameraInside) {\n" +
                        "        float D0 = 1.1 * (0.85 + 0.30 * dither);\n" +
                        "        s0 = sampleVolumetricShell(origin, ray, D0, maxDist, time, 0.35, 1.8, 0.010, 0.16, d0);\n" +
                        "    }\n" +
                        "\n" +
                        "    // Shell 1: Near Volume (2.8m)\n" +
                        "    float D1 = 2.8 * (0.85 + 0.30 * dither);\n" +
                        "    float s1 = sampleVolumetricShell(origin, ray, D1, maxDist, time, 0.55, 1.8, 0.016, 0.15, d1);\n" +
                        "\n" +
                        "    // Shell 2: Mid Volume (6.5m)\n" +
                        "    float D2 = 6.5 * (0.85 + 0.30 * dither);\n" +
                        "    float s2 = sampleVolumetricShell(origin, ray, D2, maxDist, time, 0.85, 1.8, 0.024, 0.14, d2);\n" +
                        "\n" +
                        "    // Shell 3: Distant Volume (14.0m)\n" +
                        "    float D3 = 14.0 * (0.85 + 0.30 * dither);\n" +
                        "    float s3 = sampleVolumetricShell(origin, ray, D3, maxDist, time, 1.30, 1.8, 0.040, 0.13, d3);\n" +
                        "\n" +
                        "    // Atmospheric rain mist during heavy storms\n" +
                        "    float mist = clamp((intensity - 0.30) * 0.45, 0.0, 0.35) * smoothstep(3.0, 20.0, maxDist);\n" +
                        "\n" +
                        "    return clamp(s0 * 0.85 + s1 * 0.65 + s2 * 0.45 + s3 * 0.30 + mist, 0.0, 0.88);\n" +
                        "}\n\n";

        // --- DISABLE CHUNK FADE IN SHADERS ---
        if ("stream.glsl".equals(shaderName)) {
            glsl = glsl.replace(
                    "void streamCoverage(vec3 p) {",
                    "void streamCoverage(vec3 p) {\n    return;\n"
            );
        }

        if ("scene.frag".equals(shaderName)) {
            glsl = glsl.replace(
                    "float spanCoverage(){",
                    "float spanCoverage(){\n    return 1.0;\n"
            );
        }
        // -------------------------------------

        // 1. INJECT INTO lights.glsl (World Geometry Surfaces)
        if ("lights.glsl".equals(shaderName)) {
            String uniformsAndFunc =
                    "\n// --- PZ3D ATMOSPHERE & VOLUMETRIC RAIN INJECTION ---\n" +
                            "uniform vec3 u_cameraPos;\n" +
                            "uniform vec3 u_fogColor;\n" +
                            "uniform float u_fogDensity;\n" +
                            "uniform bool u_cameraInside;\n" +
                            "uniform float u_rainIntensity;\n" +
                            "uniform float u_time;\n" +
                            "\n" +
                            "// Tests whether a 3D world-space point is outdoors\n" +
                            "bool isPointOutdoors(vec3 p) {\n" +
                            "    if (!enclosureEnabled) return true;\n" +
                            "    vec3 pEnc = p - enclosureOrigin;\n" +
                            "    if (any(lessThan(pEnc, vec3(0.0))) || any(greaterThanEqual(pEnc, enclosureSize))) return true;\n" +
                            "    ivec3 size = ivec3(round(enclosureSize / vec3(1.0, 1.0, 2.44949)));\n" +
                            "    ivec3 cell = ivec3(floor(pEnc / vec3(1.0, 1.0, 2.44949)));\n" +
                            "    return enclosureCell(cell, size).r > 0.5;\n" +
                            "}\n" +
                            "\n" +
                            sharedVolumetricRain +
                            "vec3 applyAtmosphere(vec3 surfaceColor, vec3 position, vec2 enclosure) {\n" +
                            "    float dist = length(position - u_cameraPos);\n" +
                            "    vec3 result = surfaceColor;\n" +
                            "\n" +
                            "    // --- 1. VOLUMETRIC RAIN ---\n" +
                            "    bool skipRain = u_cameraInside && (enclosure.x < 0.5) && (dist < 14.0);\n" +
                            "    if (u_rainIntensity > 0.001 && !skipRain) {\n" +
                            "        vec3 ray = (position - u_cameraPos) / max(dist, 0.001);\n" +
                            "        float rainPresence = calculateVolumetricRain(u_cameraPos, ray, dist, u_time, u_rainIntensity, u_cameraInside);\n" +
                            "        vec3 rainColor = mix(vec3(0.82, 0.86, 0.92), u_fogColor, 0.25);\n" +
                            "        result = mix(result, rainColor, rainPresence);\n" +
                            "    }\n" +
                            "\n" +
                            "    // --- 2. VOLUMETRIC FOG ---\n" +
                            "    if (u_fogDensity > 0.0001) {\n" +
                            "        float effectiveDist = dist;\n" +
                            "        if (enclosure.x < 0.5) {\n" +
                            "            if (dist <= 14.0) return result;\n" +
                            "            effectiveDist = (dist - 14.0) * 1.35;\n" +
                            "        } else if (u_cameraInside) {\n" +
                            "            effectiveDist = max(0.0, dist - 2.0);\n" +
                            "        }\n" +
                            "        float opticalDepth = effectiveDist * u_fogDensity;\n" +
                            "        float fogFactor = clamp(1.0 - exp(-opticalDepth * opticalDepth), 0.0, 1.0);\n" +
                            "        result = mix(result, u_fogColor, fogFactor);\n" +
                            "    }\n" +
                            "    return result;\n" +
                            "}\n" +
                            "// -----------------------------------------------------\n";

            glsl = glsl.replace("vec3 litSurface(", uniformsAndFunc + "vec3 litSurface(");

            String targetReturn = "return color*(fill+direct+pointIrradiance(position,normal)+torchIrradiance(position,normal));";
            String newReturn =
                    "vec3 litFinal = color*(fill+direct+pointIrradiance(position,normal)+torchIrradiance(position,normal));\n" +
                            "    return applyAtmosphere(litFinal, position, enclosure);";

            glsl = glsl.replace(targetReturn, newReturn);
        }

        // 2. INJECT INTO sky.frag (Sky Dome & Sky Rain)
        if ("sky.frag".equals(shaderName)) {
            String skyUniformsAndRain =
                    "uniform float u_fogIntensity;\n" +
                            "uniform vec3 u_fogColor;\n" +
                            "uniform float u_rainIntensity;\n" +
                            "uniform float u_time;\n" +
                            "uniform bool u_cameraInside;\n" +
                            "\n" +
                            "// Sky always treats points as outdoors\n" +
                            "bool isPointOutdoors(vec3 p) { return true; }\n" +
                            "\n" +
                            sharedVolumetricRain +
                            "vec3 applySkyRain(vec3 color, vec3 eyePos, vec3 ray) {\n" +
                            "    if (u_rainIntensity <= 0.001) return color;\n" +
                            "    float rainPresence = calculateVolumetricRain(eyePos, ray, 50.0, u_time, u_rainIntensity, u_cameraInside);\n" +
                            "    vec3 rainColor = mix(vec3(0.82, 0.86, 0.92), u_fogColor, 0.25);\n" +
                            "    return mix(color, rainColor, rainPresence);\n" +
                            "}\n";

            glsl = glsl.replace("out vec4 fragColor;", "out vec4 fragColor;\n" + skyUniformsAndRain);

            String skyTargetReturn = "fragColor=vec4(color,1);";
            String skyNewReturn =
                    "color = applySkyRain(color, eye, ray);\n" +
                            "    color = mix(color, u_fogColor, clamp(u_fogIntensity, 0.0, 1.0));\n" +
                            "    fragColor = vec4(color, 1);";

            glsl = glsl.replace(skyTargetReturn, skyNewReturn);
        }

        // 3. INJECT INTO stars.frag (Dim stars in fog)
        if ("stars.frag".equals(shaderName)) {
            String starUniforms = "uniform float u_fogIntensity;\n";
            glsl = glsl.replace("out vec4 fragColor;", "out vec4 fragColor;\n" + starUniforms);

            glsl = glsl.replace("fragColor=vec4(color,1);",
                    "color *= clamp(1.0 - u_fogIntensity, 0.0, 1.0);\n    fragColor=vec4(color,1);");
        }

        // 4. INJECT INTO ground.vert (GPU Cylindrical Billboarding)
        if ("ground.vert".equals(shaderName)) {
            String vertUniforms =
                    "uniform vec3 eye;\n" +
                            "out vec3 billboardNormal;\n";

            glsl = glsl.replace("out vec3 worldPosition;", "out vec3 worldPosition;\n" + vertUniforms);

            String oldTransform = "worldPosition=position-meshOrigin;";
            String newBillboardTransform =
                    "vec3 wPos = position - meshOrigin;\n" +
                            "    vec3 bNorm = vec3(0.0, 0.0, 1.0);\n" +
                            "    float kind = mod(overlay, 10.0);\n" +
                            "    if (abs(kind - 9.0) < 0.1) {\n" +
                            "        float hw = floor(overlay / 10.0) / 100.0;\n" +
                            "        float side = (texCoord.x > (crop.x + crop.z) * 0.5) ? 1.0 : -1.0;\n" +
                            "        vec2 toEye = eye.xy - wPos.xy;\n" +
                            "        float dLen = length(toEye);\n" +
                            "        if (dLen > 0.001) {\n" +
                            "            vec2 fwd = toEye / dLen;\n" +
                            "            vec2 right = vec2(fwd.y, -fwd.x);\n" +
                            "            wPos.xy += right * (side * hw);\n" +
                            "            bNorm = normalize(vec3(fwd * 0.45 + right * (side * 0.85), 0.35));\n" +
                            "        }\n" +
                            "    }\n" +
                            "    worldPosition = wPos;\n" +
                            "    billboardNormal = bNorm;\n";

            glsl = glsl.replace(oldTransform, newBillboardTransform);
        }

        // 5. INJECT INTO ground.frag (Curved Volumetric Lighting for Billboards)
        if ("ground.frag".equals(shaderName)) {
            glsl = glsl.replace("in vec3 worldPosition;", "in vec3 worldPosition;\nin vec3 billboardNormal;");

            String oldNormal = "if(kind==6.0||kind==7.0)normal=vec3(0,0,1);";
            String newNormal =
                    "if(kind==6.0||kind==7.0)normal=vec3(0,0,1);\n" +
                            "    if(abs(kind-9.0)<0.1)normal=billboardNormal;";

            glsl = glsl.replace(oldNormal, newNormal);
        }
    }
}