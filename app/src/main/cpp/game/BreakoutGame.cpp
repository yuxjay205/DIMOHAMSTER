#include "BreakoutGame.h"
#include "ShaderLoader.h"
#include <android/log.h>
#include <glm/gtc/type_ptr.hpp>
#include <cmath>

#define LOG_TAG "BreakoutGame"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// External functions to communicate with Java/Kotlin
namespace Game {
    extern void reportGameOver(int score, int level);
    extern void vibrate(int durationMs);
    extern void goToMainMenu();
}

namespace Game {

BreakoutGame::BreakoutGame()
    : m_state(GameState::READY)
    , m_stateTimer(0.0f)
    , m_level(1)
    , m_score(0)
    , m_lives(3)
    , m_highScore(0)
    , m_screenW(0)
    , m_screenH(0)
    , m_paddlePos(0.0f)
    , m_paddleSize(120.0f, 20.0f)
    , m_paddleSpeed(800.0f)
    , m_smoothedNosePos(0.0f)
    , m_noseSmoothingFactor(0.2f)  // Faster response for paddle control
    , m_noseSensitivity(1.0f)
    , m_ballPos(0.0f)
    , m_ballVel(0.0f)
    , m_ballRadius(8.0f)
    , m_ballSpeed(500.0f)
    , m_ballLaunched(false)
    , m_bricksRemaining(0)
    , m_paddleWidthMultiplier(1.0f)
    , m_ballSpeedMultiplier(1.0f)
    , m_ballRadiusMultiplier(1.0f)
    , m_powerUpTimer(0.0f)
    , m_screenShake(0.0f)
    , m_currentTime(0.0f)
    , m_nosePosition(0.0f)
    , m_noseDetected(false)
    , m_showCameraBackground(true)
    , m_shader(0)
    , m_vao(0)
    , m_vbo(0)
    , m_projection(1.0f)
    , m_initialized(false) {
}

BreakoutGame::~BreakoutGame() {
    shutdown();
}

void BreakoutGame::init(Engine::ShaderLoader& shaderLoader) {
    m_shader = shaderLoader.loadProgram("shaders/flat.vert", "shaders/flat.frag");
    if (m_shader == 0) {
        LOGI("Failed to load flat shader");
        return;
    }

    initGL();
    m_initialized = true;
    LOGI("BreakoutGame initialized");
}

void BreakoutGame::initGL() {
    glGenVertexArrays(1, &m_vao);
    glGenBuffers(1, &m_vbo);

    glBindVertexArray(m_vao);
    glBindBuffer(GL_ARRAY_BUFFER, m_vbo);
    glBufferData(GL_ARRAY_BUFFER, 12 * sizeof(float), nullptr, GL_DYNAMIC_DRAW);

    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(0);

    glBindVertexArray(0);
}

void BreakoutGame::shutdown() {
    if (m_vao) {
        glDeleteVertexArrays(1, &m_vao);
        m_vao = 0;
    }
    if (m_vbo) {
        glDeleteBuffers(1, &m_vbo);
        m_vbo = 0;
    }
    m_initialized = false;
}

void BreakoutGame::resize(int width, int height) {
    m_screenW = width;
    m_screenH = height;

    // Orthographic projection: origin at bottom-left, y-up
    m_projection = glm::ortho(0.0f, (float)width, 0.0f, (float)height, -1.0f, 1.0f);

    // Adjust paddle size based on screen
    m_paddleSize = glm::vec2(width * 0.15f, 15.0f);

    resetLevel();
    LOGI("Breakout resized: %dx%d", width, height);
}

void BreakoutGame::resetLevel() {
    m_paddlePos = glm::vec2(m_screenW * 0.5f, m_screenH * 0.1f);
    generateBricks();
    resetBall();
    m_state = GameState::READY;
    m_stateTimer = 0.0f;
}

void BreakoutGame::resetBall() {
    float currentRadius = m_ballRadius * m_ballRadiusMultiplier;
    m_ballPos = glm::vec2(m_paddlePos.x, m_paddlePos.y + m_paddleSize.y + currentRadius + 5.0f);
    m_ballVel = glm::vec2(0.0f);
    m_ballLaunched = false;
}

void BreakoutGame::nextLevel() {
    m_level++;

    // Ball speed increases more aggressively in infinite mode
    if (m_level <= 4) {
        m_ballSpeed += 30.0f;  // Gentle increase for tutorial levels
    } else {
        m_ballSpeed += 50.0f;  // Faster increase in infinite mode
    }

    // Cap ball speed to prevent unplayable speeds
    if (m_ballSpeed > 1000.0f) {
        m_ballSpeed = 1000.0f;
    }

    resetLevel();
    LOGI("Advanced to level %d (ball speed: %.0f)", m_level, m_ballSpeed);
}

void BreakoutGame::loseLife() {
    m_lives--;
    if (m_lives <= 0) {
        m_state = GameState::GAME_OVER;
        m_stateTimer = 0.0f;
        if (m_score > m_highScore) {
            m_highScore = m_score;
        }
        LOGI("Game Over! Final Score: %d", m_score);

        // Report game over to save high score
        reportGameOver(m_score, m_level);
    } else {
        resetBall();
        m_state = GameState::READY;
        m_stateTimer = 0.0f;
        LOGI("Life lost. Lives remaining: %d", m_lives);
    }
}

void BreakoutGame::generateBricks() {
    m_bricks.clear();
    m_bricksRemaining = 0;

    float brickWidth = (float)m_screenW / BRICKS_PER_ROW;
    float brickHeight = 25.0f;
    float startY = m_screenH * 0.65f;
    float padding = 2.0f;

    // Rainbow colors for rows
    glm::vec4 rowColors[BRICK_ROWS] = {
        glm::vec4(1.0f, 0.2f, 0.2f, 1.0f),  // Red
        glm::vec4(1.0f, 0.6f, 0.2f, 1.0f),  // Orange
        glm::vec4(1.0f, 1.0f, 0.2f, 1.0f),  // Yellow
        glm::vec4(0.2f, 1.0f, 0.2f, 1.0f),  // Green
        glm::vec4(0.2f, 0.6f, 1.0f, 1.0f),  // Blue
        glm::vec4(0.6f, 0.2f, 1.0f, 1.0f)   // Purple
    };

    // Determine number of rows based on level
    // Level 1: 1 row, Level 2: 2 rows, Level 3: 3 rows, Level 4: 4 rows
    // Level 5+: 6 rows (infinite mode - full rows)
    int numRows;
    if (m_level <= 4) {
        numRows = m_level;  // 1-4 rows for levels 1-4
    } else {
        numRows = BRICK_ROWS;  // Full 6 rows for infinite mode (level 5+)
    }

    // For infinite mode (level 5+), increase brick health based on level
    int baseHealth = (m_level >= 5) ? 1 + (m_level - 5) / 2 : 1;
    if (baseHealth > 5) baseHealth = 5;  // Cap max health at 5

    for (int row = 0; row < numRows; row++) {
        for (int col = 0; col < BRICKS_PER_ROW; col++) {
            Brick brick;
            brick.position = glm::vec2(
                col * brickWidth + padding,
                startY - row * (brickHeight + padding)
            );
            brick.size = glm::vec2(brickWidth - padding * 2, brickHeight);
            brick.color = rowColors[row % BRICK_ROWS];  // Cycle colors if needed
            brick.active = true;

            // Health based on level
            if (m_level <= 3) {
                // Levels 1-3: all bricks have 1 health (easy)
                brick.health = 1;
            } else if (m_level == 4) {
                // Level 4: only top row (row 0) has 2 health
                brick.health = (row == 0) ? 2 : 1;
            } else {
                // Infinite mode (level 5+): base health + extra for top row
                brick.health = baseHealth + ((row == 0) ? 1 : 0);
            }

            m_bricks.push_back(brick);
            m_bricksRemaining++;
        }
    }

    LOGI("Generated %d bricks (%d rows) for level %d", m_bricksRemaining, numRows, m_level);
}

void BreakoutGame::update(float dt) {
    if (!m_initialized || m_screenW == 0) return;

    m_currentTime += dt;
    m_stateTimer += dt;

    // Decay screen shake
    m_screenShake *= 0.9f;

    // Update particles
    for (auto it = m_particles.begin(); it != m_particles.end();) {
        it->life -= dt;
        if (it->life <= 0.0f) {
            it = m_particles.erase(it);
        } else {
            it->pos += it->vel * dt;
            it->vel.y -= 500.0f * dt;  // Gravity
            ++it;
        }
    }

    if (m_state == GameState::PLAYING) {
        updatePhysics(dt);
        checkCollisions();
        updatePowerUps(dt);
        checkPowerUpCollisions();

        // Decay power-up effects
        if (m_powerUpTimer > 0.0f) {
            m_powerUpTimer -= dt;
            if (m_powerUpTimer <= 0.0f) {
                m_paddleWidthMultiplier = 1.0f;
                m_ballSpeedMultiplier = 1.0f;
                m_ballRadiusMultiplier = 1.0f;
            }
        }

        // Check level complete
        if (m_bricksRemaining <= 0) {
            m_state = GameState::LEVEL_COMPLETE;
            m_stateTimer = 0.0f;
        }
    }
    else if (m_state == GameState::LEVEL_COMPLETE) {
        if (m_stateTimer > 2.0f) {
            nextLevel();
        }
    }
    else if (m_state == GameState::GAME_OVER) {
        // Don't auto-restart - wait for user to press restart button
    }
}

void BreakoutGame::updatePhysics(float dt) {
    float currentRadius = m_ballRadius * m_ballRadiusMultiplier;

    if (!m_ballLaunched) {
        // Ball follows paddle
        m_ballPos.x = m_paddlePos.x;
        m_ballPos.y = m_paddlePos.y + m_paddleSize.y + currentRadius + 5.0f;
    } else {
        // Move ball
        m_ballPos += m_ballVel * dt;

        // Wall collisions
        if (m_ballPos.x - currentRadius < 0) {
            m_ballPos.x = currentRadius;
            m_ballVel.x = -m_ballVel.x;
            m_screenShake = 5.0f;
        }
        if (m_ballPos.x + currentRadius > m_screenW) {
            m_ballPos.x = m_screenW - currentRadius;
            m_ballVel.x = -m_ballVel.x;
            m_screenShake = 5.0f;
        }
        if (m_ballPos.y + currentRadius > m_screenH) {
            m_ballPos.y = m_screenH - currentRadius;
            m_ballVel.y = -m_ballVel.y;
            m_screenShake = 5.0f;
        }

        // Bottom - lose life
        if (m_ballPos.y - currentRadius < 0) {
            loseLife();
        }
    }
}

void BreakoutGame::checkCollisions() {
    if (!m_ballLaunched) return;

    float currentRadius = m_ballRadius * m_ballRadiusMultiplier;

    // Paddle collision
    float paddleLeft = m_paddlePos.x - m_paddleSize.x * 0.5f;
    float paddleRight = m_paddlePos.x + m_paddleSize.x * 0.5f;
    float paddleTop = m_paddlePos.y + m_paddleSize.y;
    float paddleBottom = m_paddlePos.y;

    if (m_ballPos.x > paddleLeft && m_ballPos.x < paddleRight &&
        m_ballPos.y - currentRadius < paddleTop && m_ballPos.y > paddleBottom &&
        m_ballVel.y < 0) {

        // Bounce ball
        m_ballPos.y = paddleTop + currentRadius;

        // Angle based on hit position
        float hitPos = (m_ballPos.x - m_paddlePos.x) / (m_paddleSize.x * 0.5f);
        hitPos = glm::clamp(hitPos, -1.0f, 1.0f);

        float angle = hitPos * 60.0f * (3.14159f / 180.0f);  // Max 60 degrees
        float speed = glm::length(m_ballVel);
        m_ballVel.x = sin(angle) * speed;
        m_ballVel.y = fabs(cos(angle) * speed);

        m_screenShake = 8.0f;
    }

    // Brick collisions
    for (size_t i = 0; i < m_bricks.size(); i++) {
        if (m_bricks[i].active && checkBrickCollision(m_bricks[i])) {
            breakBrick(i);
            break;  // One brick per frame
        }
    }
}

bool BreakoutGame::checkBrickCollision(const Brick& brick) {
    float currentRadius = m_ballRadius * m_ballRadiusMultiplier;

    float brickLeft = brick.position.x;
    float brickRight = brick.position.x + brick.size.x;
    float brickTop = brick.position.y + brick.size.y;
    float brickBottom = brick.position.y;

    // Simple AABB collision
    if (m_ballPos.x + currentRadius > brickLeft &&
        m_ballPos.x - currentRadius < brickRight &&
        m_ballPos.y + currentRadius > brickBottom &&
        m_ballPos.y - currentRadius < brickTop) {

        // Determine bounce direction
        float overlapLeft = (m_ballPos.x + currentRadius) - brickLeft;
        float overlapRight = brickRight - (m_ballPos.x - currentRadius);
        float overlapTop = brickTop - (m_ballPos.y - currentRadius);
        float overlapBottom = (m_ballPos.y + currentRadius) - brickBottom;

        float minOverlap = glm::min(glm::min(overlapLeft, overlapRight),
                                     glm::min(overlapTop, overlapBottom));

        if (minOverlap == overlapLeft || minOverlap == overlapRight) {
            m_ballVel.x = -m_ballVel.x;
        } else {
            m_ballVel.y = -m_ballVel.y;
        }

        return true;
    }

    return false;
}

void BreakoutGame::breakBrick(int index) {
    m_bricks[index].health--;

    if (m_bricks[index].health <= 0) {
        m_bricks[index].active = false;
        m_bricksRemaining--;
        m_score += 10 * m_level;

        // Spawn particles
        glm::vec2 brickCenter = m_bricks[index].position + m_bricks[index].size * 0.5f;
        spawnParticles(brickCenter, m_bricks[index].color);

        // Pity system: lower lives = higher power-up drop chance
        // Base 20% + 15% per missing life (3 lives = 20%, 2 lives = 35%, 1 life = 50%)
        int dropChance = 20 + (3 - m_lives) * 15;
        if (dropChance > 60) dropChance = 60;  // Cap at 60%

        if ((rand() % 100) < dropChance) {
            spawnPowerUp(brickCenter);
        }

        m_screenShake = 10.0f;

        // Vibrate on brick break (stronger vibration)
        vibrate(50);

        LOGI("Brick broken! Score: %d, Remaining: %d", m_score, m_bricksRemaining);
    } else {
        // Damaged brick - darken color
        m_bricks[index].color *= 0.7f;
        m_bricks[index].color.a = 1.0f;
        m_screenShake = 5.0f;

        // Vibrate on brick hit (lighter vibration)
        vibrate(20);
    }
}

void BreakoutGame::spawnParticles(const glm::vec2& pos, const glm::vec4& color) {
    for (int i = 0; i < 12; i++) {
        Particle p;
        p.pos = pos;
        float angle = (i / 12.0f) * 6.28318f;  // 2*PI
        float speed = 100.0f + (rand() % 100);
        p.vel = glm::vec2(cos(angle) * speed, sin(angle) * speed);
        p.color = color;
        p.life = 0.5f + (rand() % 100) / 200.0f;
        p.maxLife = p.life;
        m_particles.push_back(p);
    }
}

void BreakoutGame::spawnPowerUp(const glm::vec2& pos) {
    PowerUp powerUp;
    powerUp.position = pos;

    // Random power-up type (now includes BIG_BALL)
    int type = rand() % 4;
    switch (type) {
        case 0:
            powerUp.type = PowerUpType::WIDE_PADDLE;
            powerUp.color = glm::vec4(0.3f, 0.7f, 1.0f, 1.0f);  // Blue
            break;
        case 1:
            powerUp.type = PowerUpType::SLOW_BALL;
            powerUp.color = glm::vec4(0.3f, 1.0f, 0.7f, 1.0f);  // Green
            break;
        case 2:
            powerUp.type = PowerUpType::EXTRA_LIFE;
            powerUp.color = glm::vec4(1.0f, 0.3f, 0.3f, 1.0f);  // Red
            break;
        case 3:
            powerUp.type = PowerUpType::BIG_BALL;
            powerUp.color = glm::vec4(1.0f, 0.8f, 0.2f, 1.0f);  // Orange/Gold
            break;
    }

    m_powerUps.push_back(powerUp);
    LOGI("Power-up spawned at (%.1f, %.1f)", pos.x, pos.y);
}

void BreakoutGame::updatePowerUps(float dt) {
    for (auto it = m_powerUps.begin(); it != m_powerUps.end();) {
        if (!it->active) {
            it = m_powerUps.erase(it);
            continue;
        }

        it->position += it->velocity * dt;

        // Remove if off-screen
        if (it->position.y < -50.0f) {
            it = m_powerUps.erase(it);
        } else {
            ++it;
        }
    }
}

void BreakoutGame::checkPowerUpCollisions() {
    float paddleLeft = m_paddlePos.x - m_paddleSize.x * m_paddleWidthMultiplier * 0.5f;
    float paddleRight = m_paddlePos.x + m_paddleSize.x * m_paddleWidthMultiplier * 0.5f;
    float paddleTop = m_paddlePos.y + m_paddleSize.y;
    float paddleBottom = m_paddlePos.y;

    for (auto& powerUp : m_powerUps) {
        if (!powerUp.active) continue;

        // Check collision with paddle
        if (powerUp.position.x > paddleLeft && powerUp.position.x < paddleRight &&
            powerUp.position.y < paddleTop && powerUp.position.y > paddleBottom) {

            activatePowerUp(powerUp.type);
            powerUp.active = false;
            m_screenShake = 5.0f;
        }
    }
}

void BreakoutGame::activatePowerUp(PowerUpType type) {
    switch (type) {
        case PowerUpType::WIDE_PADDLE:
            m_paddleWidthMultiplier = 1.5f;
            m_powerUpTimer = 10.0f;  // 10 seconds
            LOGI("Power-up: Wide Paddle!");
            break;

        case PowerUpType::SLOW_BALL:
            m_ballSpeedMultiplier = 0.6f;
            if (m_ballLaunched) {
                m_ballVel *= 0.6f;
            }
            m_powerUpTimer = 10.0f;
            LOGI("Power-up: Slow Ball!");
            break;

        case PowerUpType::EXTRA_LIFE:
            m_lives++;
            LOGI("Power-up: Extra Life! Total: %d", m_lives);
            break;

        case PowerUpType::MULTI_BALL:
            // Future feature
            LOGI("Power-up: Multi Ball (not implemented yet)");
            break;

        case PowerUpType::BIG_BALL:
            m_ballRadiusMultiplier = 2.0f;  // Double the ball size
            m_powerUpTimer = 10.0f;  // 10 seconds
            LOGI("Power-up: Big Ball!");
            break;
    }
}

// =============================================================================
// Input Handling
// =============================================================================

void BreakoutGame::onTouchDown(float x, float y) {
    // Convert y coordinate (touch is top-down, our coords are bottom-up)
    float gy = (float)m_screenH - y;

    if (m_state == GameState::GAME_OVER) {
        // Button dimensions (must match drawUI)
        float cx = m_screenW * 0.5f;
        float cy = m_screenH * 0.5f;
        float buttonW = 220.0f;
        float buttonH = 50.0f;
        float buttonX = cx - buttonW * 0.5f;
        float restartButtonY = cy - 40;
        float menuButtonY = restartButtonY - buttonH - 15;

        // Check if restart button was tapped
        if (x >= buttonX && x <= buttonX + buttonW &&
            gy >= restartButtonY && gy <= restartButtonY + buttonH) {
            // Restart button pressed!
            m_level = 1;
            m_score = 0;
            m_lives = 3;
            m_ballSpeed = 500.0f;
            resetLevel();
            vibrate(30);
            LOGI("Game restarted by button press!");
        }
        // Check if menu button was tapped
        else if (x >= buttonX && x <= buttonX + buttonW &&
                 gy >= menuButtonY && gy <= menuButtonY + buttonH) {
            // Menu button pressed!
            vibrate(30);
            goToMainMenu();
            LOGI("Going to main menu!");
        }
    }
    else if (m_state == GameState::READY && !m_ballLaunched) {
        // Launch ball
        float angle = 45.0f * (3.14159f / 180.0f);
        m_ballVel = glm::vec2(sin(angle) * m_ballSpeed, cos(angle) * m_ballSpeed);
        m_ballLaunched = true;
        m_state = GameState::PLAYING;
        LOGI("Ball launched!");
    }
}

void BreakoutGame::onTouchMove(float x, float y) {
    // Move paddle with touch
    m_paddlePos.x = x;
    m_paddlePos.x = glm::clamp(m_paddlePos.x, m_paddleSize.x * 0.5f,
                               m_screenW - m_paddleSize.x * 0.5f);
}

void BreakoutGame::onTouchUp(float x, float y) {
    // Not used for Breakout
}

void BreakoutGame::onNoseMoved(float normX, float normY) {
    // Update nose detection status
    m_noseDetected = true;

    // Convert to screen coordinates
    float screenX = normX * static_cast<float>(m_screenW);
    float screenY = (1.0f - normY) * static_cast<float>(m_screenH);

    // Store actual nose position for visualization
    m_nosePosition = glm::vec2(screenX, screenY);

    glm::vec2 targetNose(screenX, 0.0f);

    // Smooth nose position
    m_smoothedNosePos = m_noseSmoothingFactor * targetNose +
                       (1.0f - m_noseSmoothingFactor) * m_smoothedNosePos;

    // Move paddle with better edge handling
    // When nose is at screen edge (normX close to 0 or 1), paddle should be at edge
    float targetPaddleX = screenX;

    // Clamp to paddle boundaries
    float halfPaddleWidth = m_paddleSize.x * m_paddleWidthMultiplier * 0.5f;
    targetPaddleX = glm::clamp(targetPaddleX, halfPaddleWidth, m_screenW - halfPaddleWidth);

    m_paddlePos.x = targetPaddleX * m_noseSensitivity;
    m_paddlePos.x = glm::clamp(m_paddlePos.x, halfPaddleWidth, m_screenW - halfPaddleWidth);

    // Launch ball on upward gesture
    if (m_state == GameState::READY && !m_ballLaunched && normY < 0.3f) {
        float angle = 45.0f * (3.14159f / 180.0f);
        m_ballVel = glm::vec2(sin(angle) * m_ballSpeed, cos(angle) * m_ballSpeed);
        m_ballLaunched = true;
        m_state = GameState::PLAYING;
        LOGI("Ball launched with nose!");
    }
}

void BreakoutGame::setNoseSmoothingFactor(float factor) {
    m_noseSmoothingFactor = glm::clamp(factor, 0.0f, 1.0f);
    LOGI("Nose smoothing factor set to: %.2f", m_noseSmoothingFactor);
}

void BreakoutGame::setSensitivity(float sensitivity) {
    m_noseSensitivity = glm::clamp(sensitivity, 0.5f, 1.5f);
    LOGI("Nose sensitivity set to: %.2f", m_noseSensitivity);
}

void BreakoutGame::setTrajectoryPreviewEnabled(bool enabled) {
    // Not used in Breakout, but keep for compatibility
    (void)enabled;
}

void BreakoutGame::setShowCameraBackground(bool show) {
    m_showCameraBackground = show;
    LOGI("Camera background %s", show ? "enabled" : "disabled");
}

// =============================================================================
// Rendering
// =============================================================================

void BreakoutGame::render() {
    if (!m_initialized || m_shader == 0 || m_screenW == 0) return;

    glUseProgram(m_shader);

    // Apply screen shake
    glm::mat4 projection = m_projection;
    if (m_screenShake > 0.1f) {
        float shakeX = (rand() % 100 - 50) / 50.0f * m_screenShake;
        float shakeY = (rand() % 100 - 50) / 50.0f * m_screenShake;
        projection = glm::translate(projection, glm::vec3(shakeX, shakeY, 0.0f));
    }

    GLint mvpLoc = glGetUniformLocation(m_shader, "u_MVP");
    glUniformMatrix4fv(mvpLoc, 1, GL_FALSE, glm::value_ptr(projection));

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    // Draw background based on setting
    if (!m_showCameraBackground) {
        // Draw solid dark background
        drawQuad(0, 0, m_screenW, m_screenH, glm::vec4(0.05f, 0.05f, 0.1f, 1.0f));
    }
    // Otherwise camera shows through transparent background

    // Draw game elements
    drawBricks();
    drawPaddle();
    drawBall();
    drawPowerUps();
    drawParticles();
    drawNoseIndicator();
    drawUI();

    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);
}

void BreakoutGame::drawQuad(float x, float y, float w, float h, const glm::vec4& color) {
    float verts[] = {
        x, y,
        x + w, y,
        x + w, y + h,
        x, y,
        x + w, y + h,
        x, y + h
    };

    GLint colorLoc = glGetUniformLocation(m_shader, "u_Color");
    glUniform4fv(colorLoc, 1, glm::value_ptr(color));

    glBindVertexArray(m_vao);
    glBindBuffer(GL_ARRAY_BUFFER, m_vbo);
    glBufferSubData(GL_ARRAY_BUFFER, 0, sizeof(verts), verts);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glBindVertexArray(0);
}

void BreakoutGame::drawCircle(float x, float y, float radius, const glm::vec4& color, int segments) {
    std::vector<float> verts;
    for (int i = 0; i <= segments; i++) {
        float angle = (i / (float)segments) * 6.28318f;
        float nextAngle = ((i + 1) / (float)segments) * 6.28318f;

        verts.push_back(x);
        verts.push_back(y);
        verts.push_back(x + cos(angle) * radius);
        verts.push_back(y + sin(angle) * radius);
        verts.push_back(x + cos(nextAngle) * radius);
        verts.push_back(y + sin(nextAngle) * radius);
    }

    GLint colorLoc = glGetUniformLocation(m_shader, "u_Color");
    glUniform4fv(colorLoc, 1, glm::value_ptr(color));

    glBindVertexArray(m_vao);
    glBindBuffer(GL_ARRAY_BUFFER, m_vbo);
    glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_DYNAMIC_DRAW);
    glDrawArrays(GL_TRIANGLES, 0, (segments + 1) * 3);
    glBindVertexArray(0);
}

void BreakoutGame::drawPaddle() {
    float width = m_paddleSize.x * m_paddleWidthMultiplier;
    float x = m_paddlePos.x - width * 0.5f;
    float y = m_paddlePos.y;

    // Shadow
    drawQuad(x + 2, y - 4, width, m_paddleSize.y,
            glm::vec4(0.0f, 0.0f, 0.0f, 0.3f));

    // Main paddle - gradient effect
    glm::vec4 paddleColor = (m_paddleWidthMultiplier > 1.0f) ?
        glm::vec4(0.5f, 0.9f, 1.0f, 1.0f) :  // Brighter when powered up
        glm::vec4(0.3f, 0.7f, 1.0f, 1.0f);

    drawQuad(x, y, width, m_paddleSize.y * 0.5f, paddleColor);
    drawQuad(x, y + m_paddleSize.y * 0.5f, width, m_paddleSize.y * 0.5f,
            paddleColor * 0.8f);

    // Highlight
    drawQuad(x + 5, y + m_paddleSize.y - 3, width - 10, 2,
            glm::vec4(1.0f, 1.0f, 1.0f, 0.4f));
}

void BreakoutGame::drawBall() {
    float currentRadius = m_ballRadius * m_ballRadiusMultiplier;

    // Glow effect (bigger glow for big ball)
    float glowSize = (m_ballRadiusMultiplier > 1.0f) ? 6.0f : 4.0f;
    drawCircle(m_ballPos.x, m_ballPos.y, currentRadius + glowSize,
              glm::vec4(1.0f, 1.0f, 1.0f, 0.2f));

    // Main ball - orange tint when big
    glm::vec4 ballColor = (m_ballRadiusMultiplier > 1.0f) ?
        glm::vec4(1.0f, 0.7f, 0.2f, 1.0f) :  // Orange when powered up
        glm::vec4(1.0f, 0.9f, 0.3f, 1.0f);   // Normal yellow
    drawCircle(m_ballPos.x, m_ballPos.y, currentRadius, ballColor);

    // Highlight
    float highlightOffset = currentRadius * 0.25f;
    drawCircle(m_ballPos.x - highlightOffset, m_ballPos.y + highlightOffset,
              currentRadius * 0.4f, glm::vec4(1.0f, 1.0f, 1.0f, 0.6f));
}

void BreakoutGame::drawBricks() {
    for (const auto& brick : m_bricks) {
        if (!brick.active) continue;

        // Shadow
        drawQuad(brick.position.x + 2, brick.position.y - 2,
                brick.size.x, brick.size.y,
                glm::vec4(0.0f, 0.0f, 0.0f, 0.2f));

        // Main brick
        drawQuad(brick.position.x, brick.position.y,
                brick.size.x, brick.size.y, brick.color);

        // Highlight
        drawQuad(brick.position.x + 2, brick.position.y + brick.size.y - 4,
                brick.size.x - 4, 2,
                glm::vec4(1.0f, 1.0f, 1.0f, 0.3f));

        // Health indicator
        if (brick.health > 1) {
            float cx = brick.position.x + brick.size.x * 0.5f;
            float cy = brick.position.y + brick.size.y * 0.5f;
            drawCircle(cx, cy, 3, glm::vec4(1.0f, 1.0f, 1.0f, 0.8f));
        }
    }
}

void BreakoutGame::drawParticles() {
    for (const auto& p : m_particles) {
        float alpha = p.life / p.maxLife;
        glm::vec4 color = p.color;
        color.a = alpha;
        drawQuad(p.pos.x - 3, p.pos.y - 3, 6, 6, color);
    }
}

void BreakoutGame::drawPowerUps() {
    for (const auto& powerUp : m_powerUps) {
        if (!powerUp.active) continue;

        float halfSize = powerUp.size * 0.5f;

        // Glow effect
        drawQuad(powerUp.position.x - halfSize - 3, powerUp.position.y - halfSize - 3,
                powerUp.size + 6, powerUp.size + 6,
                glm::vec4(powerUp.color.r, powerUp.color.g, powerUp.color.b, 0.3f));

        // Main power-up box
        drawQuad(powerUp.position.x - halfSize, powerUp.position.y - halfSize,
                powerUp.size, powerUp.size, powerUp.color);

        // Icon indicator - simple shapes for each type
        float iconSize = 8.0f;
        glm::vec4 iconColor(1.0f, 1.0f, 1.0f, 0.9f);

        switch (powerUp.type) {
            case PowerUpType::WIDE_PADDLE:
                // Horizontal line
                drawQuad(powerUp.position.x - iconSize, powerUp.position.y - 1,
                        iconSize * 2, 2, iconColor);
                break;

            case PowerUpType::SLOW_BALL:
                // Circle
                drawCircle(powerUp.position.x, powerUp.position.y, iconSize * 0.5f, iconColor, 12);
                break;

            case PowerUpType::EXTRA_LIFE:
                // Heart shape (simple circles)
                drawCircle(powerUp.position.x - 3, powerUp.position.y + 2, 4, iconColor, 8);
                drawCircle(powerUp.position.x + 3, powerUp.position.y + 2, 4, iconColor, 8);
                drawQuad(powerUp.position.x - 6, powerUp.position.y - 4, 12, 6, iconColor);
                break;

            case PowerUpType::MULTI_BALL:
                // Multiple dots
                drawCircle(powerUp.position.x, powerUp.position.y, 2, iconColor, 6);
                drawCircle(powerUp.position.x - 5, powerUp.position.y, 2, iconColor, 6);
                drawCircle(powerUp.position.x + 5, powerUp.position.y, 2, iconColor, 6);
                break;

            case PowerUpType::BIG_BALL:
                // Big circle with smaller circle inside
                drawCircle(powerUp.position.x, powerUp.position.y, iconSize * 0.7f, iconColor, 12);
                drawCircle(powerUp.position.x, powerUp.position.y, iconSize * 0.3f,
                          glm::vec4(powerUp.color.r, powerUp.color.g, powerUp.color.b, 1.0f), 8);
                break;
        }
    }
}

void BreakoutGame::drawNoseIndicator() {
    if (!m_noseDetected || !m_showCameraBackground) return;

    // Draw circle outline at nose position
    float radius = 15.0f;
    glm::vec4 color(0.3f, 1.0f, 0.3f, 0.8f);  // Green

    // Outer ring
    drawCircle(m_nosePosition.x, m_nosePosition.y, radius, color, 20);

    // Inner dot
    drawCircle(m_nosePosition.x, m_nosePosition.y, 4.0f,
              glm::vec4(1.0f, 1.0f, 1.0f, 0.9f), 12);

    // Crosshair lines
    float lineLen = 10.0f;
    drawQuad(m_nosePosition.x - lineLen, m_nosePosition.y - 1, lineLen * 2, 2, color);
    drawQuad(m_nosePosition.x - 1, m_nosePosition.y - lineLen, 2, lineLen * 2, color);

    // Reset nose detected flag (will be set again on next update)
    m_noseDetected = false;
}

void BreakoutGame::drawUI() {
    // UI bar at the very top (separate from gameplay)
    float uiBarHeight = 100.0f;
    float uiBarY = m_screenH - uiBarHeight;

    // Draw semi-transparent UI background bar
    drawQuad(0, uiBarY, m_screenW, uiBarHeight,
            glm::vec4(0.0f, 0.0f, 0.0f, 0.5f));

    // Larger score display in top center
    float digitW = 40.0f;
    float digitH = 60.0f;
    float gap = 10.0f;

    // Score (centered in UI bar at -140px)
    glm::vec4 scoreColor(1.0f, 1.0f, 1.0f, 0.95f);
    int score = m_score;

    // Calculate score width to center it
    int temp = score;
    int numDigits = (score == 0) ? 1 : 0;
    while (temp > 0) { numDigits++; temp /= 10; }

    float totalWidth = numDigits * (digitW + gap) - gap;
    float startX = (m_screenW - totalWidth) * 0.5f;
    float startY = m_screenH - 200.0f;  // All UI at -200px

    if (score == 0) {
        drawDigit(0, startX, startY, digitW, digitH, scoreColor);
    } else {
        int displayScore = score;
        int temp2 = displayScore;
        int digits = 0;
        while (temp2 > 0) { digits++; temp2 /= 10; }

        float dx = startX + (digits - 1) * (digitW + gap);
        while (displayScore > 0) {
            drawDigit(displayScore % 10, dx, startY, digitW, digitH, scoreColor);
            dx -= (digitW + gap);
            displayScore /= 10;
        }
    }

    // Lives (top right in UI bar at -200px)
    glm::vec4 heartColor(1.0f, 0.3f, 0.3f, 0.9f);
    float heartX = m_screenW - 25.0f;
    float heartY = m_screenH - 200.0f;  // Same as score -200px
    for (int i = 0; i < m_lives; i++) {
        drawCircle(heartX - i * 30.0f, heartY, 10, heartColor);  // Slightly larger hearts
    }

    // Level indicator (top left in UI bar at -200px)
    float levelDigitW = 35.0f;
    float levelDigitH = 50.0f;
    float levelX = 25.0f;  // Left side
    float levelY = m_screenH - 200.0f;  // Same as score -200px

    // "LV" label
    glm::vec4 levelLabelColor(0.7f, 0.7f, 0.7f, 0.9f);
    drawQuad(levelX, levelY + levelDigitH + 5, 15, 5, levelLabelColor);
    drawQuad(levelX + 20, levelY + levelDigitH + 5, 15, 5, levelLabelColor);

    // Level number
    drawDigit(m_level, levelX + 45, levelY, levelDigitW, levelDigitH,
             glm::vec4(0.3f, 1.0f, 0.3f, 0.9f));

    // Messages
    if (m_state == GameState::READY) {
        // "Tap or nod to launch" message
        float cx = m_screenW * 0.5f;
        float cy = m_screenH * 0.4f;
        float pulse = (sin(m_currentTime * 3.0f) + 1.0f) * 0.5f;
        float alpha = 0.4f + pulse * 0.4f;

        // Simple arrow pointing up
        glm::vec4 hintColor(1.0f, 1.0f, 1.0f, alpha);
        drawQuad(cx - 2, cy, 4, 30, hintColor);
        // Arrow head - using quads
        drawQuad(cx - 10, cy + 30, 8, 2, hintColor);
        drawQuad(cx + 2, cy + 30, 8, 2, hintColor);
    }
    else if (m_state == GameState::LEVEL_COMPLETE) {
        float cx = m_screenW * 0.5f;
        float cy = m_screenH * 0.5f;

        // Fade in effect
        float alpha = glm::min(m_stateTimer / 0.5f, 1.0f);

        // Background box
        drawQuad(cx - 180, cy - 50, 360, 100,
                glm::vec4(0.0f, 0.0f, 0.0f, 0.7f * alpha));

        int nextLevel = m_level + 1;

        if (nextLevel >= 5) {
            // Entering or continuing endless mode - special message
            glm::vec4 goldColor(1.0f, 0.85f, 0.0f, alpha);

            // Draw "ENDLESS"
            float endCharW = 30.0f;
            float endCharH = 35.0f;
            float endSpacing = 5.0f;
            float endWidth = (7 * endCharW) + (6 * endSpacing);
            drawText("ENDLESS", cx - endWidth * 0.5f, cy + 10, endCharW, endCharH, endSpacing, goldColor);

            // Draw level number below
            float numWidth = 50.0f;
            drawDigit(nextLevel, cx - numWidth * 0.5f, cy - 45, numWidth, 40, goldColor);
        } else {
            // Show next level number: "LEVEL 2", "LEVEL 3", etc.
            glm::vec4 textColor(0.2f, 1.0f, 0.3f, alpha);

            // Draw "LEVEL"
            float lvlCharW = 35.0f;
            float lvlCharH = 45.0f;
            float lvlSpacing = 6.0f;
            float lvlWidth = (5 * lvlCharW) + (4 * lvlSpacing);

            // Calculate total width including number
            float numWidth = 50.0f;
            float gap = 20.0f;
            float totalWidth = lvlWidth + gap + numWidth;

            float startX = cx - totalWidth * 0.5f;

            // Draw "LEVEL"
            drawText("LEVEL", startX, cy - lvlCharH * 0.5f, lvlCharW, lvlCharH, lvlSpacing, textColor);

            // Draw level number next to it
            drawDigit(nextLevel, startX + lvlWidth + gap, cy - lvlCharH * 0.5f, numWidth, lvlCharH, textColor);
        }
    }
    else if (m_state == GameState::GAME_OVER) {
        float cx = m_screenW * 0.5f;
        float cy = m_screenH * 0.5f;

        // Fade in effect
        float alpha = glm::min(m_stateTimer / 0.5f, 1.0f);

        // Background box
        drawQuad(cx - 200, cy - 120, 400, 240,
                glm::vec4(0.0f, 0.0f, 0.0f, 0.8f * alpha));

        // Display final score (large, centered)
        float scoreDigitW = 50.0f;
        float scoreDigitH = 75.0f;
        float scoreGap = 12.0f;
        glm::vec4 scoreColor(1.0f, 1.0f, 0.3f, alpha);

        int finalScore = m_score;
        int temp2 = finalScore;
        int numDigits2 = (finalScore == 0) ? 1 : 0;
        while (temp2 > 0) { numDigits2++; temp2 /= 10; }

        float totalWidth2 = numDigits2 * (scoreDigitW + scoreGap) - scoreGap;
        float scoreStartX = (m_screenW - totalWidth2) * 0.5f;
        float scoreY = cy + 20;

        if (finalScore == 0) {
            drawDigit(0, scoreStartX, scoreY, scoreDigitW, scoreDigitH, scoreColor);
        } else {
            int displayScore2 = finalScore;
            int temp3 = displayScore2;
            int digits2 = 0;
            while (temp3 > 0) { digits2++; temp3 /= 10; }

            float dx2 = scoreStartX + (digits2 - 1) * (scoreDigitW + scoreGap);
            while (displayScore2 > 0) {
                drawDigit(displayScore2 % 10, dx2, scoreY, scoreDigitW, scoreDigitH, scoreColor);
                dx2 -= (scoreDigitW + scoreGap);
                displayScore2 /= 10;
            }
        }

        // Restart button with text
        float buttonW = 220.0f;
        float buttonH = 50.0f;
        float buttonX = cx - buttonW * 0.5f;
        float buttonY = cy - 40;

        // Button background (green)
        glm::vec4 buttonColor(0.2f, 0.8f, 0.3f, alpha);
        if (m_stateTimer > 0.5f) {
            // Pulse effect after fade in
            float pulse = (sin(m_stateTimer * 2.0f) + 1.0f) * 0.15f;
            buttonColor = glm::vec4(0.2f + pulse, 0.8f + pulse, 0.3f + pulse, alpha);
        }

        // Draw button with 3D effect
        // Shadow
        drawQuad(buttonX + 4, buttonY - 4, buttonW, buttonH,
                glm::vec4(0.0f, 0.0f, 0.0f, 0.4f * alpha));
        // Main button
        drawQuad(buttonX, buttonY, buttonW, buttonH, buttonColor);
        // Highlight
        drawQuad(buttonX + 10, buttonY + buttonH - 12, buttonW - 20, 6,
                glm::vec4(1.0f, 1.0f, 1.0f, 0.3f * alpha));

        // Draw "RESTART" text on button
        float restartCharW = 20.0f;
        float restartCharH = 26.0f;
        float restartSpacing = 3.0f;
        float restartWidth = (7 * restartCharW) + (6 * restartSpacing);  // "RESTART" = 7 chars
        float restartX = cx - restartWidth * 0.5f;
        float restartY = buttonY + (buttonH - restartCharH) * 0.5f;
        glm::vec4 textOnButtonColor(1.0f, 1.0f, 1.0f, alpha);
        drawText("RESTART", restartX, restartY, restartCharW, restartCharH, restartSpacing, textOnButtonColor);

        // Menu button (below restart)
        float menuButtonY = buttonY - buttonH - 15;

        // Button background (blue-gray)
        glm::vec4 menuButtonColor(0.4f, 0.5f, 0.6f, alpha);

        // Shadow
        drawQuad(buttonX + 4, menuButtonY - 4, buttonW, buttonH,
                glm::vec4(0.0f, 0.0f, 0.0f, 0.4f * alpha));
        // Main button
        drawQuad(buttonX, menuButtonY, buttonW, buttonH, menuButtonColor);
        // Highlight
        drawQuad(buttonX + 10, menuButtonY + buttonH - 12, buttonW - 20, 6,
                glm::vec4(1.0f, 1.0f, 1.0f, 0.3f * alpha));

        // Draw "MENU" text on button
        float menuCharW = 28.0f;
        float menuCharH = 26.0f;
        float menuSpacing = 5.0f;
        float menuWidth = (4 * menuCharW) + (3 * menuSpacing);  // "MENU" = 4 chars
        float menuTextX = cx - menuWidth * 0.5f;
        float menuTextY = menuButtonY + (buttonH - menuCharH) * 0.5f;
        drawText("MENU", menuTextX, menuTextY, menuCharW, menuCharH, menuSpacing, textOnButtonColor);
    }
}

void BreakoutGame::drawLetter(char letter, float x, float y, float w, float h, const glm::vec4& color) {
    float thick = w * 0.15f;  // Thickness of strokes

    // y is bottom of letter, y+h is top (OpenGL coords: y=0 at bottom)
    switch(letter) {
        case 'G':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x, y, w, thick, color);  // Bottom
            drawQuad(x + w - thick, y, thick, h * 0.5f, color);  // Right bottom
            drawQuad(x + w * 0.5f, y + h * 0.5f - thick * 0.5f, w * 0.5f, thick, color);  // Middle
            break;
        case 'A':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x + w - thick, y, thick, h, color);  // Right
            drawQuad(x, y + h * 0.5f - thick * 0.5f, w, thick, color);  // Middle
            break;
        case 'M':
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x + w - thick, y, thick, h, color);  // Right
            drawQuad(x + thick, y + h - thick, w * 0.35f - thick, thick, color);  // Top left
            drawQuad(x + w * 0.65f, y + h - thick, w * 0.35f - thick, thick, color);  // Top right
            drawQuad(x + w * 0.5f - thick * 0.5f, y + h * 0.5f, thick, h * 0.5f, color);  // Middle down
            break;
        case 'E':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x, y + h * 0.5f - thick * 0.5f, w * 0.8f, thick, color);  // Middle
            drawQuad(x, y, w, thick, color);  // Bottom
            break;
        case 'O':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x + w - thick, y, thick, h, color);  // Right
            drawQuad(x, y, w, thick, color);  // Bottom
            break;
        case 'V':
            drawQuad(x, y + h * 0.3f, thick * 1.5f, h * 0.7f, color);  // Left
            drawQuad(x + w - thick * 1.5f, y + h * 0.3f, thick * 1.5f, h * 0.7f, color);  // Right
            drawQuad(x + w * 0.3f, y, w * 0.4f, thick * 1.5f, color);  // Bottom point
            break;
        case 'R':
            drawQuad(x, y + h - thick, w * 0.8f, thick, color);  // Top
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x + w - thick, y + h * 0.5f, thick, h * 0.5f, color);  // Right top
            drawQuad(x, y + h * 0.5f - thick * 0.5f, w, thick, color);  // Middle
            drawQuad(x + w * 0.5f, y, thick * 1.5f, h * 0.5f, color);  // Diagonal leg
            break;
        case 'F':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x, y + h * 0.5f - thick * 0.5f, w * 0.7f, thick, color);  // Middle
            break;
        case 'I':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x + w * 0.5f - thick * 0.5f, y, thick, h, color);  // Middle
            drawQuad(x, y, w, thick, color);  // Bottom
            break;
        case 'N':
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x + w - thick, y, thick, h, color);  // Right
            drawQuad(x + thick, y + h - thick * 1.5f, w - thick * 2, thick * 1.5f, color);  // Top bar
            break;
        case 'L':
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x, y, w, thick, color);  // Bottom
            break;
        case 'S':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x, y + h * 0.5f, thick, h * 0.5f - thick, color);  // Left top
            drawQuad(x, y + h * 0.5f - thick * 0.5f, w, thick, color);  // Middle
            drawQuad(x + w - thick, y + thick, thick, h * 0.5f - thick, color);  // Right bottom
            drawQuad(x, y, w, thick, color);  // Bottom
            break;
        case 'C':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x, y, w, thick, color);  // Bottom
            break;
        case 'T':
            drawQuad(x, y + h - thick, w, thick, color);  // Top
            drawQuad(x + w * 0.5f - thick * 0.5f, y, thick, h, color);  // Middle
            break;
        case 'D':
            drawQuad(x, y, thick, h, color);  // Left
            drawQuad(x, y + h - thick, w * 0.7f, thick, color);  // Top
            drawQuad(x, y, w * 0.7f, thick, color);  // Bottom
            drawQuad(x + w - thick, y + thick, thick, h - thick * 2, color);  // Right (curved part)
            drawQuad(x + w * 0.7f, y + h - thick * 1.5f, thick, thick * 1.5f, color);  // Top corner
            drawQuad(x + w * 0.7f, y, thick, thick * 1.5f, color);  // Bottom corner
            break;
        case 'U':
            drawQuad(x, y + thick, thick, h - thick, color);  // Left
            drawQuad(x + w - thick, y + thick, thick, h - thick, color);  // Right
            drawQuad(x, y, w, thick, color);  // Bottom
            break;
    }
}

void BreakoutGame::drawText(const char* text, float x, float y, float charW, float charH, float spacing, const glm::vec4& color) {
    float currentX = x;
    for (int i = 0; text[i] != '\0'; i++) {
        if (text[i] == ' ') {
            currentX += charW * 0.6f + spacing;
        } else {
            drawLetter(text[i], currentX, y, charW, charH, color);
            currentX += charW + spacing;
        }
    }
}

void BreakoutGame::drawDigit(int digit, float x, float y, float w, float h, const glm::vec4& color) {
    float segW = w;
    float segH = h * 0.08f;
    float halfH = h * 0.5f;

    // 7-segment display
    static const bool segs[10][7] = {
        {1,1,1,0,1,1,1}, // 0
        {0,0,1,0,0,1,0}, // 1
        {1,0,1,1,1,0,1}, // 2
        {1,0,1,1,0,1,1}, // 3
        {0,1,1,1,0,1,0}, // 4
        {1,1,0,1,0,1,1}, // 5
        {1,1,0,1,1,1,1}, // 6
        {1,0,1,0,0,1,0}, // 7
        {1,1,1,1,1,1,1}, // 8
        {1,1,1,1,0,1,1}, // 9
    };

    if (digit < 0 || digit > 9) return;
    const bool* s = segs[digit];

    float vertW = segH;
    float vertH = halfH - segH;

    if (s[0]) drawQuad(x + vertW, y + h - segH, segW - vertW * 2.0f, segH, color);
    if (s[1]) drawQuad(x, y + halfH, vertW, vertH, color);
    if (s[2]) drawQuad(x + segW - vertW, y + halfH, vertW, vertH, color);
    if (s[3]) drawQuad(x + vertW, y + halfH - segH * 0.5f, segW - vertW * 2.0f, segH, color);
    if (s[4]) drawQuad(x, y + segH, vertW, vertH, color);
    if (s[5]) drawQuad(x + segW - vertW, y + segH, vertW, vertH, color);
    if (s[6]) drawQuad(x + vertW, y, segW - vertW * 2.0f, segH, color);
}

} // namespace Game
