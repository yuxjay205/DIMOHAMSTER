#pragma once

#include <GLES3/gl32.h>
#include <android/asset_manager.h>
#include <glm/glm.hpp>
#include <glm/gtc/matrix_transform.hpp>
#include <random>

namespace Engine {
class ShaderLoader;
}

namespace Game {

enum class GameState {
    READY,
    AIMING,
    THROWING,
    SCORED,
    MISSED
};

class PaperTossGame {
public:
    PaperTossGame();
    ~PaperTossGame();

    void init(Engine::ShaderLoader& shaderLoader);
    void shutdown();
    void resize(int width, int height);
    void update(float dt);
    void render();

    // Touch input
    void onTouchDown(float x, float y);
    void onTouchMove(float x, float y);
    void onTouchUp(float x, float y);

    void onNoseMoved(float normX, float normY);

    // Phase 4: Settings
    void setNoseSmoothingFactor(float factor);   // 0.0 - 1.0
    void setSensitivity(float sensitivity);       // 0.5 - 1.5
    void setTrajectoryPreviewEnabled(bool enabled);

private:
    void resetRound();
    void launchBall(float vx, float vy);
    bool checkCollision();
    bool checkCollisionAtPosition(const glm::vec2& pos, const glm::vec2& vel);
    void generateWind();

    // Phase 2: Trajectory preview
    void calculateTrajectory(const glm::vec2& velocity);
    void drawTrajectory();

    // Phase 3: Aiming reticle
    void drawAimingReticle();

    // Rendering helpers
    void initGL();
    void drawQuad(float x, float y, float w, float h, const glm::vec4& color);
    void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, const glm::vec4& color);
    void drawBackground();
    void drawBin();
    void drawPaperBall();
    void drawWindIndicator();
    void drawScore();
    void drawDigit(int digit, float x, float y, float w, float h, const glm::vec4& color);
    void drawStreakFire();
    void drawMessage();

    // Game state
    GameState m_state;
    float m_stateTimer;

    // Screen
    int m_screenW;
    int m_screenH;

    // Paper ball
    glm::vec2 m_ballPos;
    glm::vec2 m_ballVel;
    float m_ballRotation;
    float m_ballScale;

    // Ball start position (bottom center)
    glm::vec2 m_ballStart;

    // Touch tracking
    glm::vec2 m_touchStart;
    float m_touchStartTime;
    float m_currentTime;

    // Bin (trash can)
    glm::vec2 m_binPos;     // center of bin opening
    float m_binWidth;
    float m_binHeight;
    float m_binOpeningWidth; // width of the opening at top

    // Physics
    float m_gravity;
    float m_wind;

    // Score
    int m_score;
    int m_streak;
    int m_bestStreak;

    // GL resources
    GLuint m_shader;
    GLuint m_vao;
    GLuint m_vbo;
    glm::mat4 m_projection;

    // Random
    std::mt19937 m_rng;
    std::uniform_real_distribution<float> m_windDist;

    glm::vec2 m_lastNosePos;
    float m_lastNoseTime;

    // Phase 1: Nose smoothing
    glm::vec2 m_smoothedNosePos;      // Smoothed nose position
    float m_noseSmoothingFactor;       // Alpha for EMA (0.0-1.0)
    float m_noseDeadZone;              // Minimum movement threshold in pixels
    float m_noseSensitivity;           // Sensitivity multiplier (0.5-1.5)

    // Phase 2: Trajectory preview
    static const int MAX_TRAJECTORY_POINTS = 60;
    glm::vec2 m_trajectoryPoints[MAX_TRAJECTORY_POINTS];
    int m_trajectoryPointCount;
    bool m_trajectoryHitsBin;          // For color coding
    bool m_trajectoryPreviewEnabled;   // Can be toggled via settings

    bool m_initialized;
};

} // namespace Game
