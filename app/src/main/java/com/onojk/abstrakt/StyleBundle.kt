package com.onojk.abstrakt

import com.onojk.abstrakt.gl.AbstraktGLSurfaceView

data class StyleBundle(
    val name: String,
    val foldCount: Int,
    val shapeKind: ShapeKind,
    val frameShape: FrameShape,
    val invertColors: Boolean,
    val colorizeEnabled: Boolean,
    val colorizeHue: Float,
    val zoomMultiplier: Float,
    val distortionEnabled: Boolean,
    val distortionAmplitude: Float,
    val distortionFrequency: Float,
    val distortionPlusEnabled: Boolean,
    val distortionPlusYaw: Float,
    val distortionPlusPitch: Float,
    val distortionPlusRoll: Float,
    val bassZoomIntensity: Float,
    val contrast: Float,
    val contrastPasses: Int,
    val saturation: Float,
    val beatReactivity: Float,
    val chromaAberrationEnabled: Boolean,
    val chromaAberrationIntensity: Float,
    val chromaAberrationAudioReact: Boolean,
    val blackholeEnabled: Boolean,
    val blackholeStrength: Float,
    val blackholeShrinkRate: Float,
    val blackholeAlphaRadius: Float,
    val blackholeWanderAmount: Float,
) {
    companion object {
        val DEFAULT = StyleBundle(
            name               = "Default",
            foldCount          = 6,
            shapeKind          = ShapeKind.Cube,
            frameShape         = FrameShape.Circle,
            invertColors       = false,
            colorizeEnabled    = false,
            colorizeHue        = 0f,
            zoomMultiplier     = 1.0f,
            distortionEnabled  = false,
            distortionAmplitude  = 0.1f,
            distortionFrequency  = 1.0f,
            distortionPlusEnabled = false,
            distortionPlusYaw    = 0f,
            distortionPlusPitch  = 0f,
            distortionPlusRoll   = 0f,
            bassZoomIntensity  = 0.4f,
            contrast           = 1.0f,
            contrastPasses     = 1,
            saturation         = 1.0f,
            beatReactivity     = 0.4f,
            chromaAberrationEnabled  = false,
            chromaAberrationIntensity = 0.008f,
            chromaAberrationAudioReact = false,
            blackholeEnabled    = false,
            blackholeStrength   = 0.5f,
            blackholeShrinkRate = 0.97f,
            blackholeAlphaRadius  = 0.5f,
            blackholeWanderAmount = 0.005f,
        )

        val CALM = StyleBundle(
            name               = "Calm",
            foldCount          = 16,
            shapeKind          = ShapeKind.Sphere,
            frameShape         = FrameShape.Circle,
            invertColors       = false,
            colorizeEnabled    = true,
            colorizeHue        = 195f,
            zoomMultiplier     = 1.0f,
            distortionEnabled  = false,
            distortionAmplitude  = 0.1f,
            distortionFrequency  = 1.0f,
            distortionPlusEnabled = false,
            distortionPlusYaw    = 0f,
            distortionPlusPitch  = 0f,
            distortionPlusRoll   = 0f,
            bassZoomIntensity  = 0.1f,
            contrast           = 0.85f,
            contrastPasses     = 1,
            saturation         = 0.7f,
            beatReactivity     = 0.15f,
            chromaAberrationEnabled  = false,
            chromaAberrationIntensity = 0.008f,
            chromaAberrationAudioReact = false,
            blackholeEnabled    = false,
            blackholeStrength   = 0.5f,
            blackholeShrinkRate = 0.97f,
            blackholeAlphaRadius  = 0.5f,
            blackholeWanderAmount = 0.005f,
        )

        val AGGRESSIVE = StyleBundle(
            name               = "Aggressive",
            foldCount          = 6,
            shapeKind          = ShapeKind.Caltrop,
            frameShape         = FrameShape.Star,
            invertColors       = false,
            colorizeEnabled    = true,
            colorizeHue        = 0f,
            zoomMultiplier     = 1.2f,
            distortionEnabled  = true,
            distortionAmplitude  = 0.8f,
            distortionFrequency  = 6.0f,
            distortionPlusEnabled = true,
            distortionPlusYaw    = 30f,
            distortionPlusPitch  = 15f,
            distortionPlusRoll   = 45f,
            bassZoomIntensity  = 0.9f,
            contrast           = 1.8f,
            contrastPasses     = 4,
            saturation         = 1.6f,
            beatReactivity     = 0.9f,
            chromaAberrationEnabled  = false,
            chromaAberrationIntensity = 0.008f,
            chromaAberrationAudioReact = false,
            blackholeEnabled    = false,
            blackholeStrength   = 0.5f,
            blackholeShrinkRate = 0.97f,
            blackholeAlphaRadius  = 0.5f,
            blackholeWanderAmount = 0.005f,
        )

        val COSMIC = StyleBundle(
            name               = "Cosmic",
            foldCount          = 12,
            shapeKind          = ShapeKind.Icosahedron,
            frameShape         = FrameShape.Hexagon,
            invertColors       = false,
            colorizeEnabled    = true,
            colorizeHue        = 260f,
            zoomMultiplier     = 0.9f,
            distortionEnabled  = true,
            distortionAmplitude  = 0.4f,
            distortionFrequency  = 2.0f,
            distortionPlusEnabled = true,
            distortionPlusYaw    = 15f,
            distortionPlusPitch  = 8f,
            distortionPlusRoll   = 20f,
            bassZoomIntensity  = 0.5f,
            contrast           = 1.2f,
            contrastPasses     = 2,
            saturation         = 1.3f,
            beatReactivity     = 0.5f,
            chromaAberrationEnabled  = false,
            chromaAberrationIntensity = 0.008f,
            chromaAberrationAudioReact = false,
            blackholeEnabled    = true,
            blackholeStrength   = 0.72f,
            blackholeShrinkRate = 0.975f,
            blackholeAlphaRadius  = 0.30f,
            blackholeWanderAmount = 0.008f,
        )

        val VINTAGE = StyleBundle(
            name               = "Vintage",
            foldCount          = 8,
            shapeKind          = ShapeKind.Cylinder,
            frameShape         = FrameShape.Rounded,
            invertColors       = false,
            colorizeEnabled    = true,
            colorizeHue        = 35f,
            zoomMultiplier     = 1.0f,
            distortionEnabled  = true,
            distortionAmplitude  = 0.15f,
            distortionFrequency  = 2.0f,
            distortionPlusEnabled = false,
            distortionPlusYaw    = 0f,
            distortionPlusPitch  = 0f,
            distortionPlusRoll   = 0f,
            bassZoomIntensity  = 0.3f,
            contrast           = 0.9f,
            contrastPasses     = 1,
            saturation         = 0.55f,
            beatReactivity     = 0.2f,
            chromaAberrationEnabled  = false,
            chromaAberrationIntensity = 0.008f,
            chromaAberrationAudioReact = false,
            blackholeEnabled    = false,
            blackholeStrength   = 0.5f,
            blackholeShrinkRate = 0.97f,
            blackholeAlphaRadius  = 0.5f,
            blackholeWanderAmount = 0.005f,
        )

        val MINIMAL = StyleBundle(
            name               = "Minimal",
            foldCount          = 4,
            shapeKind          = ShapeKind.Cube,
            frameShape         = FrameShape.Square,
            invertColors       = false,
            colorizeEnabled    = true,
            colorizeHue        = 210f,
            zoomMultiplier     = 1.0f,
            distortionEnabled  = false,
            distortionAmplitude  = 0.1f,
            distortionFrequency  = 1.0f,
            distortionPlusEnabled = false,
            distortionPlusYaw    = 0f,
            distortionPlusPitch  = 0f,
            distortionPlusRoll   = 0f,
            bassZoomIntensity  = 0.1f,
            contrast           = 1.6f,
            contrastPasses     = 3,
            saturation         = 0.5f,
            beatReactivity     = 0.25f,
            chromaAberrationEnabled  = false,
            chromaAberrationIntensity = 0.008f,
            chromaAberrationAudioReact = false,
            blackholeEnabled    = false,
            blackholeStrength   = 0.5f,
            blackholeShrinkRate = 0.97f,
            blackholeAlphaRadius  = 0.5f,
            blackholeWanderAmount = 0.005f,
        )

        val PSYCHEDELIC = StyleBundle(
            name               = "Psychedelic",
            foldCount          = 20,
            shapeKind          = ShapeKind.Urchin,
            frameShape         = FrameShape.Flower,
            invertColors       = true,
            colorizeEnabled    = false,
            colorizeHue        = 0f,
            zoomMultiplier     = 1.3f,
            distortionEnabled  = true,
            distortionAmplitude  = 0.9f,
            distortionFrequency  = 7.5f,
            distortionPlusEnabled = true,
            distortionPlusYaw    = 60f,
            distortionPlusPitch  = 30f,
            distortionPlusRoll   = 90f,
            bassZoomIntensity  = 0.8f,
            contrast           = 1.4f,
            contrastPasses     = 6,
            saturation         = 1.8f,
            beatReactivity     = 0.85f,
            chromaAberrationEnabled  = true,
            chromaAberrationIntensity = 0.012f,
            chromaAberrationAudioReact = true,
            blackholeEnabled    = false,
            blackholeStrength   = 0.5f,
            blackholeShrinkRate = 0.97f,
            blackholeAlphaRadius  = 0.5f,
            blackholeWanderAmount = 0.005f,
        )

        val ALL: List<StyleBundle> = listOf(
            DEFAULT, CALM, AGGRESSIVE, COSMIC, VINTAGE, MINIMAL, PSYCHEDELIC,
        )
    }
}

fun StyleBundle.applyToView(view: AbstraktGLSurfaceView) {
    view.setFoldCount(foldCount)
    view.setShapeKind(shapeKind)
    view.setFrameShape(frameShape)
    view.setInvertColors(invertColors)
    view.setColorizeEnabled(colorizeEnabled)
    view.setColorizeHue(colorizeHue)
    view.setZoomMultiplier(zoomMultiplier)
    view.setDistortionEnabled(distortionEnabled)
    view.setDistortionAmplitude(distortionAmplitude)
    view.setDistortionFrequency(distortionFrequency)
    view.setDistortionPlusEnabled(distortionPlusEnabled)
    view.setDistortionPlusYaw(distortionPlusYaw)
    view.setDistortionPlusPitch(distortionPlusPitch)
    view.setDistortionPlusRoll(distortionPlusRoll)
    view.setBassZoomIntensity(bassZoomIntensity)
    view.setContrast(contrast)
    view.setContrastPasses(contrastPasses)
    view.setSaturation(saturation)
    view.setBeatReactivity(beatReactivity)
    view.setChromaAberrationEnabled(chromaAberrationEnabled)
    view.setChromaAberrationIntensity(chromaAberrationIntensity)
    view.setChromaAberrationAudioReact(chromaAberrationAudioReact)
    view.setBlackholeEnabled(blackholeEnabled)
    view.setBlackholeStrength(blackholeStrength)
    view.setBlackholeShrinkRate(blackholeShrinkRate)
    view.setBlackholeAlphaRadius(blackholeAlphaRadius)
    view.setBlackholeWanderAmount(blackholeWanderAmount)
}
