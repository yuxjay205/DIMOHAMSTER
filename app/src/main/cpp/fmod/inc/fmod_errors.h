/*
    FMOD Error String Header Placeholder

    Replace with actual FMOD SDK headers.
*/

#pragma once

#ifndef FMOD_ERRORS_H
#define FMOD_ERRORS_H

#include "fmod_common.h"

static const char* FMOD_ErrorString(FMOD_RESULT result) {
    switch (result) {
        case FMOD_OK: return "No errors.";
        default: return "Unknown error.";
    }
}

#endif // FMOD_ERRORS_H
