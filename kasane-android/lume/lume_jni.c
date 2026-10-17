/* JNI shim between app.kasane.LumeCore and the compiled Lume core.
 *
 * In compiled Lume a tensor is a pointer to its f64 elements and nothing else
 * (the shape lives in the type), so a Java double[] is passed straight in.
 * Every call is bracketed by lume_arena_mark/reset: whatever Lume allocates,
 * including the result, is released once it has been copied out. */
#include <jni.h>

long long lume_arena_mark(void);
void lume_arena_reset(long long mark);
long long momentum_step(long long m, long long decay, long long delta, long long sched);
long long period_rates(long long group, long long done, long long sched);
long long slip_lift(long long miss, long long kept, long long slip);

#define H 40
#define D 366
#define P 53

static int sized(JNIEnv *env, jdoubleArray array, jsize want) {
    return array != NULL && (*env)->GetArrayLength(env, array) == want;
}

static jdoubleArray result(JNIEnv *env, long long pointer, jsize count, long long mark) {
    jdoubleArray out = (*env)->NewDoubleArray(env, count);
    if (out != NULL) (*env)->SetDoubleArrayRegion(env, out, 0, count, (const jdouble *)pointer);
    lume_arena_reset(mark);
    return out;
}

static void throw_shape(JNIEnv *env) {
    jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (error != NULL) (*env)->ThrowNew(env, error, "array has the wrong size for core.lm");
}

#define PIN(name) jdouble *p_##name = (*env)->GetDoubleArrayElements(env, name, NULL)
#define UNPIN(name) (*env)->ReleaseDoubleArrayElements(env, name, p_##name, JNI_ABORT)

JNIEXPORT jdoubleArray JNICALL
Java_app_kasane_LumeCore_nativeMomentumStep(JNIEnv *env, jobject self, jdoubleArray m,
                                            jdoubleArray decay, jdoubleArray delta, jdoubleArray sched) {
    (void)self;
    if (!sized(env, m, H) || !sized(env, decay, H) || !sized(env, delta, H) || !sized(env, sched, H)) {
        throw_shape(env);
        return NULL;
    }
    PIN(m); PIN(decay); PIN(delta); PIN(sched);
    long long mark = lume_arena_mark();
    long long out = momentum_step((long long)p_m, (long long)p_decay, (long long)p_delta, (long long)p_sched);
    jdoubleArray copied = result(env, out, H, mark);
    UNPIN(m); UNPIN(decay); UNPIN(delta); UNPIN(sched);
    return copied;
}

JNIEXPORT jdoubleArray JNICALL
Java_app_kasane_LumeCore_nativePeriodRates(JNIEnv *env, jobject self, jdoubleArray group,
                                           jdoubleArray done, jdoubleArray sched) {
    (void)self;
    if (!sized(env, group, P * D) || !sized(env, done, D * H) || !sized(env, sched, D * H)) {
        throw_shape(env);
        return NULL;
    }
    PIN(group); PIN(done); PIN(sched);
    long long mark = lume_arena_mark();
    long long out = period_rates((long long)p_group, (long long)p_done, (long long)p_sched);
    jdoubleArray copied = result(env, out, P * H, mark);
    UNPIN(group); UNPIN(done); UNPIN(sched);
    return copied;
}

JNIEXPORT jdoubleArray JNICALL
Java_app_kasane_LumeCore_nativeSlipLift(JNIEnv *env, jobject self, jdoubleArray miss,
                                        jdoubleArray kept, jdoubleArray slip) {
    (void)self;
    if (!sized(env, miss, D * H) || !sized(env, kept, D * H) || !sized(env, slip, D)) {
        throw_shape(env);
        return NULL;
    }
    PIN(miss); PIN(kept); PIN(slip);
    long long mark = lume_arena_mark();
    long long out = slip_lift((long long)p_miss, (long long)p_kept, (long long)p_slip);
    jdoubleArray copied = result(env, out, H, mark);
    UNPIN(miss); UNPIN(kept); UNPIN(slip);
    return copied;
}
