package com.example.myfistapp.gl

import android.content.Context
import android.opengl.GLSurfaceView

class AbstraktGLSurfaceView(context: Context) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setRenderer(AbstraktRenderer())
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
