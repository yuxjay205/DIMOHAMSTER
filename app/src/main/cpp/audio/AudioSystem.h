#pragma once

#include <android/asset_manager.h>
#include <string>
#include <unordered_map>
#include <glm/glm.hpp>

// Forward declarations for FMOD types
namespace FMOD {
    class System;
    class Sound;
    class Channel;
    class ChannelGroup;

    namespace Studio {
        class System;
        class Bank;
        class EventDescription;
        class EventInstance;
        class Bus;
    }
}

namespace Audio {

struct ListenerAttributes {
    glm::vec3 position;
    glm::vec3 velocity;
    glm::vec3 forward;
    glm::vec3 up;
};

class AudioSystem {
public:
    AudioSystem();
    ~AudioSystem();

    // Lifecycle
    bool init(AAssetManager* assetManager);
    void shutdown();
    void update();

    // Bank management
    bool loadBank(const std::string& bankPath);
    void unloadBank(const std::string& bankPath);
    void unloadAllBanks();

    // Event playback
    bool playEvent(const std::string& eventPath);
    bool playEventAtPosition(const std::string& eventPath, const glm::vec3& position);
    void stopEvent(const std::string& eventPath, bool immediate = false);
    void stopAllEvents();

    // Event parameters
    void setEventParameter(const std::string& eventPath, const std::string& paramName, float value);

    // 3D Listener (maps to camera position)
    void setListenerAttributes(const ListenerAttributes& attributes);

    // Global controls
    void setMasterVolume(float volume);
    float getMasterVolume() const;
    void pauseAll(bool paused);

    // Bus controls
    void setBusVolume(const std::string& busPath, float volume);
    void setBusPaused(const std::string& busPath, bool paused);

    // State checks
    bool isInitialized() const { return m_initialized; }
    bool isEventPlaying(const std::string& eventPath) const;

private:
    // Helper functions
    std::string getFullBankPath(const std::string& bankPath);
    FMOD::Studio::EventInstance* getOrCreateEventInstance(const std::string& eventPath);

    // FMOD Systems
    FMOD::Studio::System* m_studioSystem;
    FMOD::System* m_coreSystem;

    // Asset manager for loading banks
    AAssetManager* m_assetManager;

    // Loaded banks
    std::unordered_map<std::string, FMOD::Studio::Bank*> m_banks;

    // Active event instances
    std::unordered_map<std::string, FMOD::Studio::EventInstance*> m_eventInstances;

    // State
    bool m_initialized;
    float m_masterVolume;
};

} // namespace Audio
