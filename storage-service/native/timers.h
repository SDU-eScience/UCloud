#ifndef NATIVE_TIMERS_H
#define NATIVE_TIMERS_H

#include <sys/timeb.h>
#include <ctime>
#include <cstdint>

uint64_t ms();

#define START_TIMER(a) auto timer ## a = ms();
#define END_TIMER(a) timer ## a = ms() - timer ## a;

#endif //NATIVE_TIMERS_H
