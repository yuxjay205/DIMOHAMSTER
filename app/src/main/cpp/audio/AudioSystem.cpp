#include "AudioSystem.h"
#include <android/log.h>
#include <fmod_studio.hpp>
#include <fmod.hpp>
#include <fmod_errors.h>

#define LOG_TAG "AudioSystem"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace Audio {

// Helper macro for FMOD error checking
#define FMOD_CHECK(result) \
    if (result != FMOD_OK) { \
        LOGE("FMOD error: %s", FMOD_ErrorString(result)); \
        return false; \
    }

#define FMOD_CHECK_VOID(result) \
    if (result != FMOD_OK) { \
        LOGE("FMOD error: %s", FMOD_ErrorString(result)); \
        return; \
    }

// Convert GLM vectors to FMOD_VECTOR
static FMOD_VECTOR toFmodVector(const glm::vec3& v) {
    FMOD_VECTOR fv;
    fv.x = v.x;
    fv.y = v.y;
    fv.z = v.z;
    return fv;
}

AudioSystem::AudioSystem()
    : m_studioSystem(nullptr)
    , m_coreSystem(nullptr)
    , m_assetManager(nullptr)
    , m_initialized(false)
    , m_masterVolume(1.0f) {
}

AudioSystem::~AudioSystem() {
    shutdown();
}

bool AudioSystem::init(AAssetManager* assetManager) {
    if (m_initialized) {
        LOGI("Audio system already initialized");
        return true;
    }

    m_assetManager = assetManager;

    // Create FMOD Studio system
    FMOD_RESULT result = FMOD::Studio::System::create(&m_studioSystem);
    FMOD_CHECK(result);

    // Get the core system
    result = m_studioSystem->getCoreSystem(&m_coreSystem);
    FMOD_CHECK(result);

    // Set software format for optimal Android performance
    result = m_coreSystem->setSoftwareFormat(0, FMOD_SPEAKERMODE_STEREO, 0);
    FMOD_CHECK(result);

    // Initialize the Studio system
    result = m_studioSystem->initialize(
        512,                              // Max channels
        FMOD_STUDIO_INIT_NORMAL,          // Studio flags
        FMOD_INIT_NORMAL,                 // Core flags
        nullptr                           // Extra driver data
    );
    FMOD_CHECK(result);

    m_initialized = true;
    LOGI("FMOD Audio system initialized");

    return true;
}

void AudioSystem::shutdown() {
    if (!m_initialized) {
        return;
    }

    // Stop and release all event instances
    for (auto& pair : m_eventInstances) {
        if (pair.second) {
            pair.second->stop(FMOD_STUDIO_STOP_IMMEDIATE);
            pair.second->release();
        }
    }
    m_eventInstances.clear();

    // Unload all banks
    unloadAllBanks();

    // Shutdown and release FMOD Studio system
    if (m_studioSystem) {
        m_studioSystem->release();
        m_studioSystem = nullptr;
        m_coreSystem = nullptr;  // Released with Studio system
    }

    m_initialized = false;
    LOGI("FMOD Audio system shutdown");
}

void AudioSystem::update() {
    if (!m_initialized || !m_studioSystem) {
        return;
    }

    m_studioSystem->update();
}

std::string AudioSystem::getFullBankPath(const std::string& bankPath) {
    return "fmod/" + bankPath;
}

bool AudioSystem::loadBank(const std::string& bankPath) {
    if (!m_initialized) {
        LOGE("Cannot load bank: Audio system not initialized");
        return false;
    }

    // Check if already loaded
    if (m_banks.find(bankPath) != m_banks.end()) {
        LOGI("Bank already loaded: %s", bankPath.c_str());
        return true;
    }

    std::string fullPath = getFullBankPath(bankPath);

    // Open asset
    AAsset* asset = AAssetManager_open(m_assetManager, fullPath.c_str(), AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Failed to open bank asset: %s", fullPath.c_str());
        return false;
    }

    // Get bank data
    const void* bankData = AAsset_getBuffer(asset);
    size_t bankSize = AAsset_getLength(asset);

    // Load bank from memory
    FMOD::Studio::Bank* bank = nullptr;
    FMOD_RESULT result = m_studioSystem->loadBankMemory(
        static_cast<const char*>(bankData),
        static_cast<int>(bankSize),
        FMOD_STUDIO_LOAD_MEMORY,
        FMOD_STUDIO_LOAD_BANK_NORMAL,
        &bank
    );

    AAsset_close(asset);

    if (result != FMOD_OK) {
        LOGE("Failed to load bank: %s - %s", bankPath.c_str(), FMOD_ErrorString(result));
        return false;
    }

    m_banks[bankPath] = bank;
    LOGI("Bank loaded: %s", bankPath.c_str());

    return true;
}

void AudioSystem::unloadBank(const std::string& bankPath) {
    auto it = m_banks.find(bankPath);
    if (it != m_banks.end()) {
        if (it->second) {
            it->second->unload();
        }
        m_banks.erase(it);
        LOGI("Bank unloaded: %s", bankPath.c_str());
    }
}

void AudioSystem::unloadAllBanks() {
    for (auto& pair : m_banks) {
        if (pair.second) {
            pair.second->unload();
        }
    }
    m_banks.clear();
    LOGI("All banks unloaded");
}

FMOD::Studio::EventInstance* AudioSystem::getOrCreateEventInstance(const std::string& eventPath) {
    // Check if instance already exists
    auto it = m_eventInstances.find(eventPath);
    if (it != m_eventInstances.end()) {
        return it->second;
    }

    // Get event description
    FMOD::Studio::EventDescription* eventDesc = nullptr;
    FMOD_RESULT result = m_studioSystem->getEvent(eventPath.c_str(), &eventDesc);
    if (result != FMOD_OK) {
        LOGE("Failed to get event description: %s - %s", eventPath.c_str(), FMOD_ErrorString(result));
        return nullptr;
    }

    // Create instance
    FMOD::Studio::EventInstance* instance = nullptr;
    result = eventDesc->createInstance(&instance);
    if (result != FMOD_OK) {
        LOGE("Failed to create event instance: %s - %s", eventPath.c_str(), FMOD_ErrorString(result));
        return nullptr;
    }

    m_eventInstances[eventPath] = instance;
    return instance;
}

bool AudioSystem::playEvent(const std::string& eventPath) {
    if (!m_initialized) {
        return false;
    }

    FMOD::Studio::EventInstance* instance = getOrCreateEventInstance(eventPath);
    if (!instance) {
        return false;
    }

    FMOD_RESULT result = instance->start();
    FMOD_CHECK(result);

    LOGI("Playing event: %s", eventPath.c_str());
    return true;
}

bool AudioSystem::playEventAtPosition(const std::string& eventPath, const glm::vec3& position) {
    if (!m_initialized) {
        return false;
    }

    FMOD::Studio::EventInstance* instance = getOrCreateEventInstance(eventPath);
    if (!instance) {
        return false;
    }

    // Set 3D attributes
    FMOD_3D_ATTRIBUTES attributes = {};
    attributes.position = toFmodVector(position);
    attributes.velocity = {0.0f, 0.0f, 0.0f};
    attributes.forward = {0.0f, 0.0f, 1.0f};
    attributes.up = {0.0f, 1.0f, 0.0f};

    FMOD_RESULT result = instance->set3DAttributes(&attributes);
    FMOD_CHECK(result);

    result = instance->start();
    FMOD_CHECK(result);

    return true;
}

void AudioSystem::stopEvent(const std::string& eventPath, bool immediate) {
    auto it = m_eventInstances.find(eventPath);
    if (it != m_eventInstances.end() && it->second) {
        it->second->stop(immediate ? FMOD_STUDIO_STOP_IMMEDIATE : FMOD_STUDIO_STOP_ALLOWFADEOUT);
    }
}

void AudioSystem::stopAllEvents() {
    for (auto& pair : m_eventInstances) {
        if (pair.second) {
            pair.second->stop(FMOD_STUDIO_STOP_IMMEDIATE);
        }
    }
}

void AudioSystem::setEventParameter(const std::string& eventPath,
                                     const std::string& paramName, float value) {
    auto it = m_eventInstances.find(eventPath);
    if (it != m_eventInstances.end() && it->second) {
        it->second->setParameterByName(paramName.c_str(), value);
    }
}

void AudioSystem::setListenerAttributes(const ListenerAttributes& attributes) {
    if (!m_initialized || !m_studioSystem) {
        return;
    }

    FMOD_3D_ATTRIBUTES fmodAttribs = {};
    fmodAttribs.position = toFmodVector(attributes.position);
    fmodAttribs.velocity = toFmodVector(attributes.velocity);
    fmodAttribs.forward = toFmodVector(attributes.forward);
    fmodAttribs.up = toFmodVector(attributes.up);

    m_studioSystem->setListenerAttributes(0, &fmodAttribs);
}

void AudioSystem::setMasterVolume(float volume) {
    m_masterVolume = volume;

    if (!m_initialized) {
        return;
    }

    FMOD::Studio::Bus* masterBus = nullptr;
    if (m_studioSystem->getBus("bus:/", &masterBus) == FMOD_OK && masterBus) {
        masterBus->setVolume(volume);
    }
}

float AudioSystem::getMasterVolume() const {
    return m_masterVolume;
}

void AudioSystem::pauseAll(bool paused) {
    if (!m_initialized) {
        return;
    }

    FMOD::Studio::Bus* masterBus = nullptr;
    if (m_studioSystem->getBus("bus:/", &masterBus) == FMOD_OK && masterBus) {
        masterBus->setPaused(paused);
    }
}

void AudioSystem::setBusVolume(const std::string& busPath, float volume) {
    if (!m_initialized) {
        return;
    }

    FMOD::Studio::Bus* bus = nullptr;
    if (m_studioSystem->getBus(busPath.c_str(), &bus) == FMOD_OK && bus) {
        bus->setVolume(volume);
    }
}

void AudioSystem::setBusPaused(const std::string& busPath, bool paused) {
    if (!m_initialized) {
        return;
    }

    FMOD::Studio::Bus* bus = nullptr;
    if (m_studioSystem->getBus(busPath.c_str(), &bus) == FMOD_OK && bus) {
        bus->setPaused(paused);
    }
}

bool AudioSystem::isEventPlaying(const std::string& eventPath) const {
    auto it = m_eventInstances.find(eventPath);
    if (it == m_eventInstances.end() || !it->second) {
        return false;
    }

    FMOD_STUDIO_PLAYBACK_STATE state;
    if (it->second->getPlaybackState(&state) == FMOD_OK) {
        return state == FMOD_STUDIO_PLAYBACK_PLAYING;
    }

    return false;
}

} // namespace Audio
