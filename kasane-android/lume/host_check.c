/* Calls the compiled Lume core exactly as the JNI shim will: tensors are
 * plain double arrays, each call bracketed by an arena mark and reset. */
#include <math.h>
#include <stdio.h>
#include <string.h>

long long lume_arena_mark(void);
void lume_arena_reset(long long mark);
long long momentum_step(long long m, long long decay, long long delta, long long sched);
long long period_rates(long long group, long long done, long long sched);
long long slip_lift(long long miss, long long kept, long long slip);

#define H 40
#define D 366
#define P 53
static double m[H], decay[H], delta[H], sched[H], out[H];
static double group[P * D], done[D * H], due[D * H], rates[P * H];
static double miss[D * H], kept[D * H], slip[D], lift[H];
static int failures;

static void expect(const char *what, double got, double want) {
    int ok = fabs(got - want) < 1e-9;
    printf("%s  %-34s got %.9f want %.9f\n", ok ? "PASS" : "FAIL", what, got, want);
    if (!ok) failures++;
}

int main(void) {
    long long start = lume_arena_mark();
    for (int round = 0; round < 1000; round++) {
        long long mark = lume_arena_mark();
        for (int h = 0; h < H; h++) { m[h] = 0.5; decay[h] = exp(-0.08); delta[h] = 0; sched[h] = 1; }
        delta[1] = 0.18; delta[2] = 0.28; m[2] = 0.95; sched[3] = 0;
        memcpy(out, (double *)momentum_step((long long)m, (long long)decay, (long long)delta, (long long)sched), sizeof out);
        lume_arena_reset(mark);
    }
    expect("missed day decays", out[0], 0.5 * exp(-0.08));
    expect("target day adds 0.18", out[1], 0.5 * exp(-0.08) + 0.18);
    expect("stretch caps at 1", out[2], 1.0);
    expect("unscheduled day unchanged", out[3], 0.5);

    /* Week 0 = days 0..6. Habit 0 due all 7 days, done on 5. Habit 1 never due. */
    for (int d = 0; d < 7; d++) { group[0 * D + d] = 1; due[d * H + 0] = 1; done[d * H + 0] = d < 5; }
    long long mark = lume_arena_mark();
    memcpy(rates, (double *)period_rates((long long)group, (long long)done, (long long)due), sizeof rates);
    lume_arena_reset(mark);
    expect("week rate 5 of 7", rates[0 * H + 0], 5.0 / 7.0 * 7.0 / (7.0 + 1e-6));
    expect("never-due habit rate is 0", rates[0 * H + 1], 0.0);

    /* Habit 0: missed on 4 days, 3 of them followed by a slip; done on 6
     * days, 1 followed by a slip. Lift = 3/4 - 1/6. */
    for (int d = 0; d < 4; d++) { miss[d * H] = 1; slip[d] = d < 3; }
    for (int d = 4; d < 10; d++) { kept[d * H] = 1; slip[d] = d == 4; }
    mark = lume_arena_mark();
    memcpy(lift, (double *)slip_lift((long long)miss, (long long)kept, (long long)slip), sizeof lift);
    lume_arena_reset(mark);
    expect("slip lift for habit 0", lift[0], 3.0 / (4 + 1e-6) - 1.0 / (6 + 1e-6));
    expect("no data gives 0 lift", lift[5], 0.0);

    expect("arena back where it started", (double)lume_arena_mark(), (double)start);
    printf(failures ? "FAILED %d\n" : "ALL PASS\n", failures);
    return failures != 0;
}
