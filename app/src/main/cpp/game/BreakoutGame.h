#pragma once

#include <GLES3/gl32.h>
#include <android/asset_manager.h>
#include <glm/glm.hpp>
#include <glm/gtc/matrix_transform.hpp>
#include <random>
#include <vector>

namespace Engine {
class ShaderLoader;
}

namespace Game {

enum class GameState {
    READY,          // Waiting to start
    PLAYING,        // Active gameplay
    LEVEL_COMPLETE, // Level finished
    GAME_OVER       // No lives left
};

struct Brick {
    glm::vec2 position;
    glm::vec2 size;
    glm::vec4 color;
    bool active;
    int health;     // Number of hits to break

    Brick() : position(0.0f), size(0.0f), color(1.0f), active(true), health(1) {}
};

enum class PowerUpType {
    WIDE_PADDLE,   // Makes paddle wider
    SLOW_BALL,     // Slows ball down
    EXTRA_LIFE,    // Gives extra life
    MULTI_BALL     // Spawns extra balls (future)
};

struct PowerUp {
    glm::vec2 position;
    glm::vec2 velocity;
    PowerUpType type;
    glm::vec4 color;
    bool active;
    float size;

    PowerUp() : position(0.0f), velocity(0.0f, -150.0f), type(PowerUpType::WIDE_PADDLE),
                color(1.0f), active(true), size(20.0f) {}
};

class BreakoutGame {
public:
    BreakoutGame();
    ~BreakoutGame();

    void init(Engine::ShaderLoader& shaderLoader);
    void shutdown();
    void resize(int width, int height);
    void update(float dt);
    void render();

    // Input
    void onTouchDown(float x, float y);
    void onTouchMove(float x, float y);
    void onTouchUp(float x, float y);
    void onNoseMoved(float normX, float normY);

    // Settings
    void setNoseSmoothingFactor(float factor);
    void setSensitivity(float sensitivity);
    void setTrajectoryPreviewEnabled(bool enabled);
    void setShowCameraBackground(bool show);

private:
    void resetLevel();
    void resetBall();
    void nextLevel();
    void loseLife();
    void generateBricks();
    void updatePhysics(float dt);
    void checkCollisions();
    bool checkBrickCollision(const Brick& brick);
    void breakBrick(int index);
    void spawnParticles(const glm::vec2& pos, const glm::vec4& color);
    void spawnPowerUp(const glm::vec2& pos);
    void updatePowerUps(float dt);
    void checkPowerUpCollisions();
    void activatePowerUp(PowerUpType type);

    // Rendering
    void initGL();
    void drawQuad(float x, float y, float w, float h, const glm::vec4& color);
    void drawCircle(float x, float y, float radius, const glm::vec4& color, int segments = 20);
    void drawPaddle();
    void drawBall();
    void drawBricks();
    void drawParticles();
    void drawPowerUps();
    void drawNoseIndicator();
    void drawUI();
    void drawLetter(char letter, float x, float y, float w, float h, const glm::vec4& color);
    void drawText(const char* text, float x, float y, float charW, float charH, float spacing, const glm::vec4& color);
    void drawDigit(int digit, float x, float y, float w, float h, const glm::vec4& color);

    // Game state
    GameState m_state;
    float m_stateTimer;
    int m_level;
    int m_score;
    int m_lives;
    int m_highScore;

    // Screen
    int m_screenW;
    int m_screenH;

    // Paddle
    glm::vec2 m_paddlePos;
    glm::vec2 m_paddleSize;
    float m_paddleSpeed;
    glm::vec2 m_smoothedNosePos;
    float m_noseSmoothingFactor;
    float m_noseSensitivity;

    // Ball
    glm::vec2 m_ballPos;
    glm::vec2 m_ballVel;
    float m_ballRadius;
    float m_ballSpeed;
    bool m_ballLaunched;

    // Bricks
    std::vector<Brick> m_bricks;
    int m_bricksRemaining;
    static const int BRICKS_PER_ROW = 8;
    static const int BRICK_ROWS = 6;

    // Power-ups
    std::vector<PowerUp> m_powerUps;
    float m_paddleWidthMultiplier;
    float m_ballSpeedMultiplier;
    float m_powerUpTimer;

    // Particles
    struct Particle {
        glm::vec2 pos;
        glm::vec2 vel;
        glm::vec4 color;
        float life;
        float maxLife;
    };
    std::vector<Particle> m_particles;

    // Visual effects
    float m_screenShake;
    float m_currentTime;

    // Nose tracking visualization
    glm::vec2 m_nosePosition;
    bool m_noseDetected;

    // Settings
    bool m_showCameraBackground;

    // GL resources
    GLuint m_shader;
    GLuint m_vao;
    GLuint m_vbo;
    glm::mat4 m_projection;

    bool m_initialized;
};

} // namespace Game
