#pragma once
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>

// Subtract background time without putting a mutex on every guest mftb.
// The sequence and fields are ALL sequentially consistent atomics: this is not
// a seqlock over racy plain data (which would be undefined C++ on ARM64).
// Writers are rare, serialized lifecycle transitions. Readers get one coherent
// snapshot; a timestamp sampled just before a transition is clamped to it.
class PausedClock {
    std::mutex writer_;
    std::atomic<uint64_t> sequence_{0}, transition_{0}, excluded_{0};
    std::atomic<bool> paused_{false};
public:
    void SetPaused(bool value, uint64_t now) {
        std::lock_guard<std::mutex> lock(writer_);
        if (paused_.load() == value) return;
        sequence_.fetch_add(1);
        now = std::max(now, transition_.load());
        if (!value) excluded_.fetch_add(now - transition_.load());
        transition_.store(now);
        paused_.store(value);
        sequence_.fetch_add(1);
    }
    uint64_t Read(uint64_t now) const {
        for (;;) {
            const uint64_t version = sequence_.load();
            if (version & 1) continue;
            const bool paused = paused_.load();
            const uint64_t transition = transition_.load(), excluded = excluded_.load();
            if (sequence_.load() == version)
                return (paused ? transition : std::max(now, transition)) - excluded;
        }
    }
};
