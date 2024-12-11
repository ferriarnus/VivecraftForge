#version 330 core

uniform sampler2D thirdPersonColor;
uniform sampler2D thirdPersonDepth;

uniform vec3 hmdViewPosition;
uniform vec3 hmdPlaneNormal;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform int firstPersonPass;
uniform vec3 keyColor;
uniform int alphaMode;

in vec2 texCoordinates;

out vec4 out_Color;

vec3 getFragmentPosition(in vec2 coord) {
    vec4 posScreen = vec4(coord * 2.0 - 1.0, texture(thirdPersonDepth, coord).x * 2.0 - 1.0, 1);
    vec4 posView = inverse(projectionMatrix * viewMatrix) * posScreen;
    return posView.xyz / posView.w;
}

vec3 avoidKeyColor(in vec3 color) {
    // make sure colors don't match keyColor
    if (all(lessThan(color - keyColor, vec3(0.004)))) {
        if (all(lessThan(keyColor, vec3(0.004)))) {
            // if key is black add
            return color + 0.004;
        } else {
            return color - 0.004;
        }
    } else {
        return color;
    }
}

void main(void) {

    out_Color = vec4(keyColor, 1.0);
    if (firstPersonPass == 1) {
        // unity like
        vec2 sampleTexcCoord = fract(texCoordinates * 2.0);
        if (texCoordinates.x < 0.5 && texCoordinates.y < 0.5) {
            // third person all
            out_Color.rgb = texture(thirdPersonColor, sampleTexcCoord).rgb;
        } else if (texCoordinates.y >= 0.5){
            // third person front
            vec3 fragPos = getFragmentPosition(sampleTexcCoord);
            if (dot(fragPos - hmdViewPosition, hmdPlaneNormal) >= 0.0) {
                if (texCoordinates.x < 0.5) {
                    // color
                    out_Color.rgb = texture(thirdPersonColor, sampleTexcCoord).rgb;
                    if (alphaMode == 0) {
                        out_Color.rgb = avoidKeyColor(out_Color.rgb);
                    }
                } else if (alphaMode == 1){
                    // white mask
                    out_Color.rgb = vec3(1.0);
                }
            }
        }
    } else {
        // side by side
        vec2 sampleTexcCoord = fract(texCoordinates * vec2(2.0, 1.0));
        if (texCoordinates.x >= 0.5) {
            // third person all
            out_Color.rgb = texture(thirdPersonColor, sampleTexcCoord).rgb;
        } else {
            // third person front
            vec3 fragPos = getFragmentPosition(sampleTexcCoord);
            if (dot(fragPos - hmdViewPosition, hmdPlaneNormal) >= 0.0) {
                // color
                out_Color.rgb = avoidKeyColor(texture(thirdPersonColor, sampleTexcCoord).rgb);
            }
        }
    }
}
