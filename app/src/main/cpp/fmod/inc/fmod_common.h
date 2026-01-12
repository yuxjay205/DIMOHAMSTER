/*
    FMOD Common Header Placeholder

    Replace with actual FMOD SDK headers.
*/

#pragma once

#ifndef FMOD_COMMON_H
#define FMOD_COMMON_H

typedef int FMOD_RESULT;
typedef unsigned int FMOD_MODE;
typedef unsigned int FMOD_TIMEUNIT;
typedef unsigned int FMOD_INITFLAGS;
typedef unsigned int FMOD_SPEAKERMODE;

#define FMOD_OK 0
#define FMOD_INIT_NORMAL 0x00000000
#define FMOD_SPEAKERMODE_STEREO 3

typedef struct {
    float x;
    float y;
    float z;
} FMOD_VECTOR;

typedef struct {
    FMOD_VECTOR position;
    FMOD_VECTOR velocity;
    FMOD_VECTOR forward;
    FMOD_VECTOR up;
} FMOD_3D_ATTRIBUTES;

#endif // FMOD_COMMON_H
