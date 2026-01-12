/*
    FMOD Studio API Header Placeholder

    This is a placeholder file for development/CI builds.
    Replace with actual FMOD SDK headers from:
    https://www.fmod.com/download

    Required files from FMOD Studio API (Android):
    - fmod_studio.hpp
    - fmod_studio_common.h
*/

#pragma once

#ifndef FMOD_STUDIO_HPP
#define FMOD_STUDIO_HPP

#include "fmod.hpp"
#include "fmod_studio_common.h"

namespace FMOD {
namespace Studio {

class System {
public:
    static FMOD_RESULT create(System** system) { *system = nullptr; return FMOD_OK; }
    FMOD_RESULT getCoreSystem(FMOD::System** system) { return FMOD_OK; }
    FMOD_RESULT initialize(int maxchannels, unsigned int studioflags, unsigned int flags, void* extradriverdata) { return FMOD_OK; }
    FMOD_RESULT release() { return FMOD_OK; }
    FMOD_RESULT update() { return FMOD_OK; }
    FMOD_RESULT loadBankMemory(const char* buffer, int length, unsigned int mode, unsigned int flags, Bank** bank) { return FMOD_OK; }
    FMOD_RESULT getEvent(const char* path, EventDescription** event) { return FMOD_OK; }
    FMOD_RESULT getBus(const char* path, Bus** bus) { return FMOD_OK; }
    FMOD_RESULT setListenerAttributes(int listener, const FMOD_3D_ATTRIBUTES* attributes) { return FMOD_OK; }
};

class Bank {
public:
    FMOD_RESULT unload() { return FMOD_OK; }
};

class EventDescription {
public:
    FMOD_RESULT createInstance(EventInstance** instance) { return FMOD_OK; }
};

class EventInstance {
public:
    FMOD_RESULT start() { return FMOD_OK; }
    FMOD_RESULT stop(unsigned int mode) { return FMOD_OK; }
    FMOD_RESULT release() { return FMOD_OK; }
    FMOD_RESULT set3DAttributes(const FMOD_3D_ATTRIBUTES* attributes) { return FMOD_OK; }
    FMOD_RESULT setParameterByName(const char* name, float value, bool ignoreseekspeed = false) { return FMOD_OK; }
    FMOD_RESULT getPlaybackState(FMOD_STUDIO_PLAYBACK_STATE* state) { *state = FMOD_STUDIO_PLAYBACK_STOPPED; return FMOD_OK; }
};

class Bus {
public:
    FMOD_RESULT setVolume(float volume) { return FMOD_OK; }
    FMOD_RESULT setPaused(bool paused) { return FMOD_OK; }
};

} // namespace Studio
} // namespace FMOD

#endif // FMOD_STUDIO_HPP
