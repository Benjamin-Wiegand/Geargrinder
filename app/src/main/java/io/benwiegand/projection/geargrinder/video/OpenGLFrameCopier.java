package io.benwiegand.projection.geargrinder.video;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * GLES/EGL frame copier/limiter
 */
public class OpenGLFrameCopier implements FrameCopier {
    private static final String TAG = OpenGLFrameCopier.class.getSimpleName();

    private static final String VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
            """;


    private final Object lock = new Object();
    private final Thread renderThread = new Thread(this::renderLoop, TAG);

    private final float[] matrix = new float[16];   // 4x4 matrix
    private final FloatBuffer vertexBuffer = createDirectFloatBuffer(new float[] {
            -1f, -1f,
            1f, -1f,
            -1f,  1f,
            1f,  1f,
    });
    private final FloatBuffer texBuffer = createDirectFloatBuffer(new float[] {
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
    });

    private final int tex;
    private int program = 0;

    private int aPosition = 0;
    private int aTexCoord = 0;
    private int uTexMatrix = 0;
    private int uTexture = 0;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    private final SurfaceTexture surfaceTexture;
    private final Surface inputSurface;
    private final Surface outputSurface;

    private final int width;
    private final int height;

    private Throwable initError = null;

    private boolean initComplete = false;
    private boolean nextFrameAvailable = false;
    private boolean copyFrame = false;
    private boolean dead = false;

    private int frameNumber = 0;

    public OpenGLFrameCopier(int width, int height, Surface outputSurface) {
        this.width = width;
        this.height = height;
        this.outputSurface = outputSurface;

        int[] texArray = new int[1];
        GLES20.glGenTextures(1, texArray, 0);
        checkErrorGL();

        tex = texArray[0];

        surfaceTexture = new SurfaceTexture(tex);
        surfaceTexture.setDefaultBufferSize(width, height);
        surfaceTexture.setOnFrameAvailableListener(this::onFrameAvailable);
        inputSurface = new Surface(surfaceTexture);

        renderThread.start();
    }

    private static void checkErrorEGL() {
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            Log.e(TAG, "EGL error: " + error);
            throw new RuntimeException("EGL error = " + error);
        }
    }

    private static void checkErrorGL() {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "GLES error: " + error);
            throw new RuntimeException("GLES error = " + error);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        checkErrorGL();

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, "failed to compile shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("failed to compile shader");
        }
        return shader;
    }

    private static int createProgram() {
        int program = GLES20.glCreateProgram();
        checkErrorGL();

        Log.v(TAG, "compiling shaders");
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        GLES20.glAttachShader(program, vertexShader);
        checkErrorGL();

        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        GLES20.glAttachShader(program, fragmentShader);
        checkErrorGL();

        Log.v(TAG, "linking program");
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "failed to link program: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("failed to link program");
        }

        return program;
    }

    private static FloatBuffer createDirectFloatBuffer(float[] src) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(src.length * Float.BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(src);
        floatBuffer.position(0);
        return floatBuffer;
    }

    private void initGL() {
        boolean status;

        int[] configAttributes = new int[] {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
        };

        int[] contextAttributes = new int[] {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };

        int[] surfaceAttributes = new int[] {
                EGL14.EGL_NONE
        };

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);

        int[] version = new int[2];
        status = EGL14.eglInitialize(eglDisplay, version, 0, version, 1);
        if (!status) throw new RuntimeException("failed to initialize EGL");

        EGLConfig[] configArray = new EGLConfig[1];
        int[] numConfigArray = new int[1];

        status = EGL14.eglChooseConfig(
                eglDisplay,
                configAttributes, 0,
                configArray, 0, configArray.length,
                numConfigArray, 0);
        if (!status) throw new RuntimeException("failed to initialize EGL");
        if (numConfigArray[0] == 0) throw new RuntimeException("no EGL config");

        EGLConfig eglConfig = configArray[0];

        eglContext = EGL14.eglCreateContext(
                eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
                contextAttributes, 0);
        checkErrorEGL();

        eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay, eglConfig, outputSurface,
                surfaceAttributes, 0);
        checkErrorEGL();

        status = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
        if (!status) throw new RuntimeException("failed to initialize EGL");

        program = createProgram();
        GLES20.glUseProgram(program);

        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix");
        uTexture = GLES20.glGetUniformLocation(program, "sTexture");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glUniform1i(uTexture, 0);
        checkErrorGL();
    }

    @Override
    public void init() throws Throwable {
        // in the spirit of init(), this just blocks and throws an error if there is one
        synchronized (lock) {
            if (!initComplete)
                lock.wait();

            if (!initComplete)
                throw new IllegalStateException("init didn't complete");

            if (initError != null)
                throw initError;
        }
    }

    @Override
    public void destroy() {
        if (dead) return;
        dead = true;
        renderThread.interrupt();

        EGL14.eglDestroySurface(eglDisplay, eglSurface);
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(eglDisplay);
    }

    @Override
    public Surface getInputSurface() {
        if (dead) throw new IllegalStateException("frame copier is dead");
        return inputSurface;
    }

    @Override
    public int nextFrameNumber() {
        if (dead) throw new IllegalStateException("frame copier is dead");
        return frameNumber;
    }

    @Override
    public int copyFrame() {
        try {
            synchronized (lock) {
                if (dead) throw new IllegalStateException("frame copier is dead");
                copyFrame = true;
                lock.notify();
                lock.wait();
                return frameNumber;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted");
            return -1;
        }
    }

    private void renderLoop() {
        // everything needs to happen on the same thread
        synchronized (lock) {
            try {

                try {
                    initGL();
                } catch (Throwable t) {
                    Log.e(TAG, "init failed", t);
                    initError = t;
                    return;
                } finally {
                    initComplete = true;
                    lock.notify();
                }

                while (!dead) {
                    if (nextFrameAvailable) {
                        surfaceTexture.updateTexImage();
                        surfaceTexture.getTransformMatrix(matrix);

                        if (++frameNumber == Integer.MAX_VALUE) frameNumber = 0;
                        nextFrameAvailable = false;
                    }

                    if (copyFrame) {
                        GLES20.glUseProgram(program);

                        GLES20.glViewport(0, 0, width, height);
                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                        GLES20.glEnableVertexAttribArray(aPosition);
                        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

                        GLES20.glEnableVertexAttribArray(aTexCoord);
                        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuffer);

                        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, matrix, 0);

                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, System.nanoTime());
                        EGL14.eglSwapBuffers(eglDisplay, eglSurface);

                        checkErrorGL();
                        checkErrorEGL();

                        copyFrame = false;
                        lock.notify();
                    }

                    lock.wait();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "render loop interrupted");
            } finally {
                dead = true;
                lock.notify();
            }
        }
    }

    private void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (lock) {
            nextFrameAvailable = true;
            lock.notify();
        }
    }
}
