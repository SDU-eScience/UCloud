#include "timers.h"

uint64_t ms() {
    /*
    struct timeb timer_msec{};
    ftime(&timer_msec);
    return ((uint64_t) timer_msec.time) * 1000ll + (uint64_t) timer_msec.millitm;
     */

    clock_t t = (clock() * 1000000) / CLOCKS_PER_SEC;
    return t;
}