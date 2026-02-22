#include "PaperTossGame.h"
#include "ShaderLoader.h"
#include <android/log.h>
#include <glm/gtc/type_ptr.hpp>
#include <cmath>
#include <chrono>

#define LOG_TAG "PaperTossGame"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace Game {

PaperTossGame::PaperTossGame()
    : m_state(GameState::READY)
    , m_stateTimer(0.0f)
    , m_screenW(0)
    , m_screenH(0)
    , m_ballPos(0.0f)
    , m_ballVel(0.0f)
    , m_ballRotation(0.0f)
    , m_ballScale(1.0f)
    , m_ballStart(0.0f)
    , m_touchStart(0.0f)
    , m_touchStartTime(0.0f)
    , m_currentTime(0.0f)
    , m_binPos(0.0f)
    , m_binWidth(120.0f)
    , m_binHeight(150.0f)
    , m_binOpeningWidth(100.0f)
    , m_gravity(-1800.0f)
    , m_wind(0.0f)
    , m_score(0)
    , m_streak(0)
    , m_bestStreak(0)
    , m_shader(0)
    , m_vao(0)
    , m_vbo(0)
    , m_projection(1.0f)
    , m_rng(std::random_device{}())
    , m_windDist(-3.0f, 3.0f)
    , m_smoothedNosePos(0.0f)
    , m_noseSmoothingFactor(0.3f)  // Default: heavy smoothing
    , m_noseDeadZone(5.0f)          // Ignore movements < 5px
    , m_noseSensitivity(1.0f)       // Default: normal sensitivity
    , m_trajectoryPointCount(0)
    , m_trajectoryHitsBin(false)
    , m_trajectoryPreviewEnabled(true)  // Enabled by default
    , m_initialized(false) {
}

PaperTossGame::~PaperTossGame() {
    shutdown();
}

void PaperTossGame::init(Engine::ShaderLoader& shaderLoader) {
    m_shader = shaderLoader.loadProgram("shaders/flat.vert", "shaders/flat.frag");
    if (m_shader == 0) {
        LOGI("Failed to load flat shader");
        return;
    }

    initGL();
    generateWind();
    m_initialized = true;
    LOGI("PaperTossGame initialized");
}

void PaperTossGame::initGL() {
    // Create a VAO/VBO for dynamic quad rendering (6 vertices for 2 triangles)
    glGenVertexArrays(1, &m_vao);
    glGenBuffers(1, &m_vbo);

    glBindVertexArray(m_vao);
    glBindBuffer(GL_ARRAY_BUFFER, m_vbo);
    // Allocate enough for a quad (6 vertices * 2 floats)
    glBufferData(GL_ARRAY_BUFFER, 12 * sizeof(float), nullptr, GL_DYNAMIC_DRAW);

    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(0);

    glBindVertexArray(0);
}

void PaperTossGame::shutdown() {
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

void PaperTossGame::resize(int width, int height) {
    m_screenW = width;
    m_screenH = height;

    // Orthographic projection: origin at bottom-left, y-up
    m_projection = glm::ortho(0.0f, (float)width, 0.0f, (float)height, -1.0f, 1.0f);

    // Ball starts at bottom center
    m_ballStart = glm::vec2(width * 0.5f, height * 0.12f);

    // Bin at top center
    m_binPos = glm::vec2(width * 0.5f, height * 0.75f);
    m_binWidth = width * 0.12f;
    m_binHeight = width * 0.15f;
    m_binOpeningWidth = m_binWidth * 0.85f;

    resetRound();
    LOGI("Game resized: %dx%d", width, height);
}

void PaperTossGame::resetRound() {
    m_ballPos = m_ballStart;
    m_ballVel = glm::vec2(0.0f);
    m_ballRotation = 0.0f;
    m_ballScale = 1.0f;
    m_state = GameState::READY;
    m_stateTimer = 0.0f;
}

void PaperTossGame::generateWind() {
    m_wind = m_windDist(m_rng);
}

void PaperTossGame::update(float dt) {
    if (!m_initialized || m_screenW == 0) return;

    m_currentTime += dt;
    m_stateTimer += dt;

    if (m_state == GameState::THROWING) {
        // Apply wind and gravity
        m_ballVel.x += m_wind * 200.0f * dt;
        m_ballVel.y += m_gravity * dt;
        m_ballPos += m_ballVel * dt;

        // Rotate ball during flight
        m_ballRotation += 360.0f * dt;

        // Scale ball to simulate depth (bigger near bottom, smaller near top)
        float t = (m_ballPos.y - m_ballStart.y) / (m_binPos.y - m_ballStart.y);
        t = glm::clamp(t, 0.0f, 1.0f);
        m_ballScale = glm::mix(1.0f, 0.55f, t);

        // Check scoring collision
        if (checkCollision()) {
            m_state = GameState::SCORED;
            m_stateTimer = 0.0f;
            m_score++;
            m_streak++;
            if (m_streak > m_bestStreak) m_bestStreak = m_streak;
            LOGI("SCORED! Score: %d, Streak: %d", m_score, m_streak);
        }
        // Check miss (ball went off screen)
        else if (m_ballPos.y < -50.0f || m_ballPos.x < -100.0f ||
                 m_ballPos.x > m_screenW + 100.0f || m_ballPos.y > m_screenH + 100.0f) {
            m_state = GameState::MISSED;
            m_stateTimer = 0.0f;
            m_streak = 0;
            LOGI("MISSED! Streak reset");
        }
    }
    else if (m_state == GameState::SCORED || m_state == GameState::MISSED) {
        if (m_stateTimer > 1.0f) {
            generateWind();
            resetRound();
        }
    }
}

bool PaperTossGame::checkCollision() {
    return checkCollisionAtPosition(m_ballPos, m_ballVel);
}

bool PaperTossGame::checkCollisionAtPosition(const glm::vec2& pos, const glm::vec2& vel) {
    // Check if ball center is within bin opening rectangle
    float halfOpening = m_binOpeningWidth * 0.5f;
    float binTop = m_binPos.y + m_binHeight * 0.3f;
    float binBottom = m_binPos.y - m_binHeight * 0.1f;

    return pos.x > (m_binPos.x - halfOpening) &&
           pos.x < (m_binPos.x + halfOpening) &&
           pos.y < binTop &&
           pos.y > binBottom &&
           vel.y < 0.0f; // ball must be falling down
}

void PaperTossGame::launchBall(float vx, float vy) {
    m_ballVel = glm::vec2(vx, vy);
    m_ballPos = m_ballStart;
    m_state = GameState::THROWING;
    m_stateTimer = 0.0f;
}

// Phase 2: Calculate trajectory preview
void PaperTossGame::calculateTrajectory(const glm::vec2& initialVelocity) {
    const float dt = 1.0f / 30.0f;  // 30 FPS simulation
    const float maxTime = 2.0f;     // 2 seconds ahead

    glm::vec2 pos = m_ballStart;
    glm::vec2 vel = initialVelocity;

    m_trajectoryPointCount = 0;
    m_trajectoryHitsBin = false;

    for (float t = 0; t < maxTime && m_trajectoryPointCount < MAX_TRAJECTORY_POINTS; t += dt) {
        // Apply physics (same as update())
        vel.x += m_wind * 200.0f * dt;
        vel.y += m_gravity * dt;
        pos += vel * dt;

        // Store point
        m_trajectoryPoints[m_trajectoryPointCount++] = pos;

        // Check bin collision
        if (checkCollisionAtPosition(pos, vel)) {
            m_trajectoryHitsBin = true;
            break;
        }

        // Stop if off-screen
        if (pos.y < -100 || pos.y > m_screenH + 100 ||
            pos.x < -100 || pos.x > m_screenW + 100) {
            break;
        }
    }
}

void PaperTossGame::onTouchDown(float x, float y) {
    if (m_state != GameState::READY) return;

    // Convert screen coords: touch y is top-down, our coords are bottom-up
    float gy = (float)m_screenH - y;

    m_touchStart = glm::vec2(x, gy);
    m_touchStartTime = m_currentTime;
    m_state = GameState::AIMING;
}

void PaperTossGame::onTouchMove(float x, float y) {
    if (m_state != GameState::AIMING || !m_trajectoryPreviewEnabled) return;

    // Convert screen coords
    float gy = (float)m_screenH - y;
    glm::vec2 touchCurrent(x, gy);

    float elapsed = m_currentTime - m_touchStartTime;
    if (elapsed < 0.01f) elapsed = 0.01f;

    glm::vec2 delta = touchCurrent - m_touchStart;

    // Only show preview if swiping upward with some force
    if (delta.y > 10.0f) {
        float speed = glm::length(delta) / elapsed;
        speed = glm::clamp(speed, 200.0f, 4000.0f);

        glm::vec2 dir = glm::normalize(delta);
        glm::vec2 vel = dir * speed;

        // Clamp to same ranges as actual launch
        vel.y = glm::clamp(vel.y, 600.0f, 2500.0f);
        vel.x = glm::clamp(vel.x, -800.0f, 800.0f);

        // Phase 2: Calculate trajectory for preview
        calculateTrajectory(vel);
    } else {
        // No valid trajectory
        m_trajectoryPointCount = 0;
    }
}

void PaperTossGame::onTouchUp(float x, float y) {
    if (m_state != GameState::AIMING) return;

    float gy = (float)m_screenH - y;
    glm::vec2 touchEnd(x, gy);

    float elapsed = m_currentTime - m_touchStartTime;
    if (elapsed < 0.01f) elapsed = 0.01f;

    glm::vec2 delta = touchEnd - m_touchStart;

    // Calculate velocity from swipe: direction * speed multiplier
    // Only launch if swiped upward with enough force
    if (delta.y > 20.0f) {
        float speed = glm::length(delta) / elapsed;
        speed = glm::clamp(speed, 200.0f, 4000.0f);

        glm::vec2 dir = glm::normalize(delta);
        glm::vec2 vel = dir * speed;

        // Clamp vertical to reasonable range
        vel.y = glm::clamp(vel.y, 600.0f, 2500.0f);
        // Limit horizontal
        vel.x = glm::clamp(vel.x, -800.0f, 800.0f);

        launchBall(vel.x, vel.y);
    } else {
        // Swipe too weak, go back to ready
        m_state = GameState::READY;
    }
}

// =============================================================================
// Rendering
// =============================================================================

void PaperTossGame::render() {
    if (!m_initialized || m_shader == 0 || m_screenW == 0) return;

    glUseProgram(m_shader);

    // Set projection
    GLint mvpLoc = glGetUniformLocation(m_shader, "u_MVP");
    glUniformMatrix4fv(mvpLoc, 1, GL_FALSE, glm::value_ptr(m_projection));

    // Disable depth test for 2D
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

//    drawBackground();
    drawBin();
    drawWindIndicator();

    // Phase 2 & 3: Draw trajectory and reticle during aiming
    if (m_state == GameState::AIMING) {
        drawTrajectory();
        drawAimingReticle();
    }

    drawPaperBall();
    drawScore();
    if (m_streak >= 3) drawStreakFire();
    drawMessage();

    // Re-enable
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);
}

void PaperTossGame::drawQuad(float x, float y, float w, float h, const glm::vec4& color) {
    float x0 = x, y0 = y;
    float x1 = x + w, y1 = y + h;

    float verts[] = {
        x0, y0,
        x1, y0,
        x1, y1,
        x0, y0,
        x1, y1,
        x0, y1
    };

    GLint colorLoc = glGetUniformLocation(m_shader, "u_Color");
    glUniform4fv(colorLoc, 1, glm::value_ptr(color));

    glBindVertexArray(m_vao);
    glBindBuffer(GL_ARRAY_BUFFER, m_vbo);
    glBufferSubData(GL_ARRAY_BUFFER, 0, sizeof(verts), verts);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glBindVertexArray(0);
}

void PaperTossGame::drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, const glm::vec4& color) {
    float verts[] = { x1, y1, x2, y2, x3, y3 };

    GLint colorLoc = glGetUniformLocation(m_shader, "u_Color");
    glUniform4fv(colorLoc, 1, glm::value_ptr(color));

    glBindVertexArray(m_vao);
    glBindBuffer(GL_ARRAY_BUFFER, m_vbo);
    glBufferSubData(GL_ARRAY_BUFFER, 0, sizeof(verts), verts);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);
}

void PaperTossGame::drawBackground() {
    float w = (float)m_screenW;
    float h = (float)m_screenH;

    // Wall (upper portion) - beige/office color
    drawQuad(0, h * 0.15f, w, h * 0.85f, glm::vec4(0.85f, 0.82f, 0.75f, 1.0f));

    // Floor (lower portion) - dark brown
    drawQuad(0, 0, w, h * 0.15f, glm::vec4(0.35f, 0.25f, 0.18f, 1.0f));

    // Baseboard line
    drawQuad(0, h * 0.15f, w, h * 0.008f, glm::vec4(0.55f, 0.45f, 0.35f, 1.0f));
}

void PaperTossGame::drawBin() {
    float cx = m_binPos.x;
    float by = m_binPos.y - m_binHeight * 0.5f;
    float ty = m_binPos.y + m_binHeight * 0.5f;
    float bw = m_binWidth * 0.5f;        // half-width at bottom
    float tw = m_binOpeningWidth * 0.5f;  // half-width at top (opening)

    // Bin body: dark gray trapezoid (two triangles)
    glm::vec4 binColor(0.35f, 0.35f, 0.38f, 1.0f);
    drawTriangle(cx - bw, by, cx + bw, by, cx + tw, ty, binColor);
    drawTriangle(cx - bw, by, cx + tw, ty, cx - tw, ty, binColor);

    // Bin rim: slightly lighter
    glm::vec4 rimColor(0.5f, 0.5f, 0.52f, 1.0f);
    float rimH = m_binHeight * 0.06f;
    drawQuad(cx - tw - 4.0f, ty - rimH, (tw + 4.0f) * 2.0f, rimH * 2.0f, rimColor);

    // Inner shadow at top
    glm::vec4 innerColor(0.15f, 0.15f, 0.18f, 0.7f);
    float innerW = tw * 0.85f;
    drawQuad(cx - innerW, ty - rimH * 3.0f, innerW * 2.0f, rimH * 3.0f, innerColor);

    // Scored flash effect
    if (m_state == GameState::SCORED && m_stateTimer < 0.4f) {
        float flash = 1.0f - (m_stateTimer / 0.4f);
        glm::vec4 flashColor(0.2f, 1.0f, 0.3f, flash * 0.5f);
        drawQuad(cx - tw, by, tw * 2.0f, m_binHeight, flashColor);
    }
}

void PaperTossGame::drawPaperBall() {
    if (m_state == GameState::SCORED) return; // ball is in the bin

    float size = 30.0f * m_ballScale;

    // Draw the paper ball as a rotated quad
    float cx = m_ballPos.x;
    float cy = m_ballPos.y;
    float halfSize = size * 0.5f;

    float rad = glm::radians(m_ballRotation);
    float cosR = cos(rad);
    float sinR = sin(rad);

    // Rotated corners
    auto rotPoint = [&](float lx, float ly) -> glm::vec2 {
        return glm::vec2(cx + lx * cosR - ly * sinR,
                         cy + lx * sinR + ly * cosR);
    };

    glm::vec2 p0 = rotPoint(-halfSize, -halfSize);
    glm::vec2 p1 = rotPoint( halfSize, -halfSize);
    glm::vec2 p2 = rotPoint( halfSize,  halfSize);
    glm::vec2 p3 = rotPoint(-halfSize,  halfSize);

    // White paper ball
    glm::vec4 paperColor(0.95f, 0.93f, 0.88f, 1.0f);
    drawTriangle(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, paperColor);
    drawTriangle(p0.x, p0.y, p2.x, p2.y, p3.x, p3.y, paperColor);

    // Crumple lines (slightly darker)
    glm::vec4 lineColor(0.75f, 0.72f, 0.68f, 1.0f);
    float lw = size * 0.06f;
    // Diagonal crease
    glm::vec2 l0 = rotPoint(-halfSize * 0.4f, -halfSize * 0.6f);
    glm::vec2 l1 = rotPoint( halfSize * 0.5f,  halfSize * 0.3f);
    glm::vec2 l2 = rotPoint( halfSize * 0.5f + lw,  halfSize * 0.3f + lw);
    glm::vec2 l3 = rotPoint(-halfSize * 0.4f + lw, -halfSize * 0.6f + lw);
    drawTriangle(l0.x, l0.y, l1.x, l1.y, l2.x, l2.y, lineColor);
    drawTriangle(l0.x, l0.y, l2.x, l2.y, l3.x, l3.y, lineColor);

    // Shadow under ball when on ground
    if (m_state == GameState::READY || m_state == GameState::AIMING) {
        glm::vec4 shadowColor(0.0f, 0.0f, 0.0f, 0.15f);
        drawQuad(cx - halfSize * 1.2f, cy - halfSize - 5.0f, halfSize * 2.4f, 6.0f, shadowColor);
    }
}

void PaperTossGame::drawWindIndicator() {
    if (m_state == GameState::SCORED || m_state == GameState::MISSED) return;

    float cx = m_screenW * 0.5f;
    float cy = m_screenH * 0.92f;

    // Label area background
    float bgW = 200.0f;
    drawQuad(cx - bgW, cy - 15.0f, bgW * 2.0f, 35.0f, glm::vec4(0.0f, 0.0f, 0.0f, 0.25f));

    // Arrow shaft
    float arrowLen = m_wind * 30.0f; // scale wind to pixels
    glm::vec4 arrowColor;

    float absWind = fabs(m_wind);
    if (absWind < 1.0f) {
        arrowColor = glm::vec4(0.3f, 0.9f, 0.3f, 0.9f); // green: calm
    } else if (absWind < 2.0f) {
        arrowColor = glm::vec4(0.9f, 0.9f, 0.2f, 0.9f); // yellow: moderate
    } else {
        arrowColor = glm::vec4(0.9f, 0.3f, 0.2f, 0.9f); // red: strong
    }

    float shaftH = 6.0f;
    if (fabs(arrowLen) > 2.0f) {
        drawQuad(cx, cy - shaftH * 0.5f, arrowLen, shaftH, arrowColor);

        // Arrow head
        float headSize = 14.0f;
        float tipX = cx + arrowLen;
        float dir = arrowLen > 0 ? 1.0f : -1.0f;
        drawTriangle(tipX, cy - headSize,
                     tipX, cy + headSize,
                     tipX + headSize * dir, cy,
                     arrowColor);
    } else {
        // Very little wind - show dot
        drawQuad(cx - 4.0f, cy - 4.0f, 8.0f, 8.0f, arrowColor);
    }
}

void PaperTossGame::drawDigit(int digit, float x, float y, float w, float h, const glm::vec4& color) {
    // 7-segment display style digit rendering
    float segW = w;
    float segH = h * 0.08f;
    float halfH = h * 0.5f;

    //  _    seg 0 (top)
    // |_|   seg 1 (top-left), seg 2 (top-right), seg 3 (middle)
    // |_|   seg 4 (bottom-left), seg 5 (bottom-right), seg 6 (bottom)

    // Segments: which are on for each digit
    // Format: top, top-left, top-right, middle, bottom-left, bottom-right, bottom
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

    float vertW = segH;       // width of vertical segment
    float vertH = halfH - segH; // height of vertical segment

    // Top horizontal
    if (s[0]) drawQuad(x + vertW, y + h - segH, segW - vertW * 2.0f, segH, color);
    // Top-left vertical
    if (s[1]) drawQuad(x, y + halfH, vertW, vertH, color);
    // Top-right vertical
    if (s[2]) drawQuad(x + segW - vertW, y + halfH, vertW, vertH, color);
    // Middle horizontal
    if (s[3]) drawQuad(x + vertW, y + halfH - segH * 0.5f, segW - vertW * 2.0f, segH, color);
    // Bottom-left vertical
    if (s[4]) drawQuad(x, y + segH, vertW, vertH, color);
    // Bottom-right vertical
    if (s[5]) drawQuad(x + segW - vertW, y + segH, vertW, vertH, color);
    // Bottom horizontal
    if (s[6]) drawQuad(x + vertW, y, segW - vertW * 2.0f, segH, color);
}

void PaperTossGame::drawScore() {
    float digitW = 30.0f;
    float digitH = 50.0f;
    float gap = 8.0f;
    float startX = 20.0f;
    float startY = m_screenH - 70.0f;

    glm::vec4 scoreColor(1.0f, 1.0f, 1.0f, 0.9f);

    // Draw score digits
    int s = m_score;
    if (s == 0) {
        drawDigit(0, startX, startY, digitW, digitH, scoreColor);
    } else {
        // Count digits
        int temp = s;
        int numDigits = 0;
        while (temp > 0) { numDigits++; temp /= 10; }

        float dx = startX + (numDigits - 1) * (digitW + gap);
        while (s > 0) {
            drawDigit(s % 10, dx, startY, digitW, digitH, scoreColor);
            dx -= (digitW + gap);
            s /= 10;
        }
    }

    // Draw streak indicator on the right side
    if (m_streak > 0) {
        glm::vec4 streakColor(1.0f, 0.7f, 0.2f, 0.9f);
        float sx = m_screenW - 20.0f - digitW;
        float sy = startY;

        int st = m_streak;
        if (st == 0) {
            drawDigit(0, sx, sy, digitW, digitH, streakColor);
        } else {
            while (st > 0) {
                drawDigit(st % 10, sx, sy, digitW, digitH, streakColor);
                sx -= (digitW + gap);
                st /= 10;
            }
        }

        // "x" indicator (small cross next to streak)
        float xx = m_screenW - 20.0f;
        float xy = sy + digitH * 0.3f;
        float cs = 8.0f;
        drawQuad(xx, xy - 1.5f, cs, 3.0f, streakColor);
        drawQuad(xx + cs * 0.5f - 1.5f, xy - cs * 0.5f, 3.0f, cs, streakColor);
    }
}

void PaperTossGame::drawStreakFire() {
    // Draw flame triangles near the score when streak >= 3
    float fx = 20.0f;
    float fy = m_screenH - 85.0f;

    float flicker = sin(m_currentTime * 12.0f) * 5.0f;

    glm::vec4 orangeColor(1.0f, 0.5f, 0.0f, 0.8f);
    glm::vec4 redColor(1.0f, 0.2f, 0.0f, 0.6f);
    glm::vec4 yellowColor(1.0f, 0.9f, 0.1f, 0.7f);

    int flames = glm::min(m_streak - 2, 5);
    for (int i = 0; i < flames; i++) {
        float ox = (float)i * 18.0f;
        float h = 20.0f + flicker + (float)i * 3.0f;

        drawTriangle(fx + ox, fy,
                     fx + ox + 14.0f, fy,
                     fx + ox + 7.0f, fy - h, orangeColor);
        drawTriangle(fx + ox + 3.0f, fy,
                     fx + ox + 11.0f, fy,
                     fx + ox + 7.0f, fy - h * 0.7f, yellowColor);
    }

    // Big flame on top if streak >= 5
    if (m_streak >= 5) {
        float bigH = 30.0f + flicker * 1.5f;
        drawTriangle(fx - 5.0f, fy,
                     fx + flames * 18.0f + 5.0f, fy,
                     fx + flames * 9.0f, fy - bigH, redColor);
    }
}

void PaperTossGame::drawMessage() {
    // Show a brief message on score/miss
    if (m_state == GameState::SCORED && m_stateTimer < 0.8f) {
        float alpha = 1.0f - (m_stateTimer / 0.8f);
        float cx = m_screenW * 0.5f;
        float cy = m_screenH * 0.5f + m_stateTimer * 100.0f; // float upward

        // Green "check" shape
        glm::vec4 color(0.1f, 0.9f, 0.2f, alpha);
        float s = 30.0f;
        // Left stroke of check
        drawQuad(cx - s * 0.5f, cy - s * 0.1f, s * 0.35f, s * 0.08f, color);
        // Right stroke of check
        drawQuad(cx - s * 0.2f, cy - s * 0.1f, s * 0.8f, s * 0.08f, color);
    }
    else if (m_state == GameState::MISSED && m_stateTimer < 0.8f) {
        float alpha = 1.0f - (m_stateTimer / 0.8f);
        float cx = m_screenW * 0.5f;
        float cy = m_screenH * 0.5f;

        // Red "X" shape
        glm::vec4 color(0.9f, 0.15f, 0.1f, alpha);
        float s = 25.0f;
        // Two crossing bars approximated as quads
        drawQuad(cx - s, cy - 3.0f, s * 2.0f, 6.0f, color);
        drawQuad(cx - 3.0f, cy - s, 6.0f, s * 2.0f, color);
    }

    // "Swipe up!" hint when ready
    if (m_state == GameState::READY && m_stateTimer > 0.5f) {
        float pulse = (sin(m_currentTime * 3.0f) + 1.0f) * 0.5f;
        float alpha = 0.3f + pulse * 0.4f;

        float cx = m_screenW * 0.5f;
        float cy = m_ballStart.y + 80.0f;

        // Up arrow hint
        glm::vec4 hintColor(1.0f, 1.0f, 1.0f, alpha);
        float aw = 4.0f;
        float ah = 35.0f;
        // Arrow shaft
        drawQuad(cx - aw * 0.5f, cy, aw, ah, hintColor);
        // Arrow head
        drawTriangle(cx - 12.0f, cy + ah,
                     cx + 12.0f, cy + ah,
                     cx, cy + ah + 18.0f,
                     hintColor);
    }
}

// Phase 2: Draw trajectory preview
void PaperTossGame::drawTrajectory() {
    if (!m_trajectoryPreviewEnabled || m_trajectoryPointCount == 0) return;

    // Choose color: green if hits bin, white otherwise
    glm::vec4 baseColor = m_trajectoryHitsBin ?
                          glm::vec4(0.2f, 1.0f, 0.3f, 0.8f) :
                          glm::vec4(1.0f, 1.0f, 1.0f, 0.7f);

    // Draw small circles at each trajectory point
    const float circleRadius = 4.0f;
    for (int i = 0; i < m_trajectoryPointCount; i++) {
        // Fade alpha along path
        float t = static_cast<float>(i) / static_cast<float>(m_trajectoryPointCount);
        float alpha = baseColor.a * (1.0f - t * 0.5f);  // Fade to 50% at end

        glm::vec4 color = baseColor;
        color.a = alpha;

        // Draw circle as small quad
        glm::vec2 pos = m_trajectoryPoints[i];
        drawQuad(pos.x - circleRadius, pos.y - circleRadius,
                circleRadius * 2.0f, circleRadius * 2.0f, color);
    }
}

// Phase 3: Draw aiming reticle
void PaperTossGame::drawAimingReticle() {
    if (!m_trajectoryPreviewEnabled || m_trajectoryPointCount == 0) return;

    // Get final trajectory point
    glm::vec2 targetPos = m_trajectoryPoints[m_trajectoryPointCount - 1];

    // Pulsing scale animation
    float pulse = 1.0f + 0.2f * sin(m_currentTime * 4.0f);

    // Color matches trajectory
    glm::vec4 color = m_trajectoryHitsBin ?
                      glm::vec4(0.2f, 1.0f, 0.3f, 0.9f) :
                      glm::vec4(1.0f, 1.0f, 1.0f, 0.8f);

    float radius = 15.0f * pulse;
    float thickness = 3.0f;

    // Draw circle as 4 arcs (approximated with quads)
    // Top
    drawQuad(targetPos.x - thickness * 0.5f, targetPos.y + radius - thickness,
            thickness, thickness, color);
    // Bottom
    drawQuad(targetPos.x - thickness * 0.5f, targetPos.y - radius,
            thickness, thickness, color);
    // Left
    drawQuad(targetPos.x - radius, targetPos.y - thickness * 0.5f,
            thickness, thickness, color);
    // Right
    drawQuad(targetPos.x + radius - thickness, targetPos.y - thickness * 0.5f,
            thickness, thickness, color);

    // Crosshair lines
    float crosshairLen = 8.0f;
    // Horizontal
    drawQuad(targetPos.x - crosshairLen, targetPos.y - 1.0f,
            crosshairLen * 2.0f, 2.0f, color);
    // Vertical
    drawQuad(targetPos.x - 1.0f, targetPos.y - crosshairLen,
            2.0f, crosshairLen * 2.0f, color);
}

// Phase 4: Settings setters
void PaperTossGame::setNoseSmoothingFactor(float factor) {
    m_noseSmoothingFactor = glm::clamp(factor, 0.0f, 1.0f);
    LOGI("Nose smoothing factor set to: %.2f", m_noseSmoothingFactor);
}

void PaperTossGame::setSensitivity(float sensitivity) {
    m_noseSensitivity = glm::clamp(sensitivity, 0.5f, 1.5f);
    LOGI("Nose sensitivity set to: %.2f", m_noseSensitivity);
}

void PaperTossGame::setTrajectoryPreviewEnabled(bool enabled) {
    m_trajectoryPreviewEnabled = enabled;
    LOGI("Trajectory preview %s", enabled ? "enabled" : "disabled");
}

void PaperTossGame::onNoseMoved(float normX, float normY) {
    if (m_state != GameState::READY && m_state != GameState::AIMING)
        return;

    float screenX = normX * static_cast<float>(m_screenW);
    float screenY = (1.0f - normY) * static_cast<float>(m_screenH);
    glm::vec2 currentNose(screenX, screenY);

    if (m_state == GameState::READY) {
        m_lastNosePos = currentNose;
        m_smoothedNosePos = currentNose;  // Initialize smoothed position
        m_lastNoseTime = m_currentTime;
        m_state = GameState::AIMING;
        return;
    }

    if (m_state == GameState::AIMING) {
        // Phase 1: Apply exponential moving average smoothing
        m_smoothedNosePos = m_noseSmoothingFactor * currentNose +
                           (1.0f - m_noseSmoothingFactor) * m_smoothedNosePos;

        float elapsed = m_currentTime - m_lastNoseTime;
        if (elapsed < 0.016f)
            return;

        // Check dead zone - ignore small movements
        glm::vec2 delta = m_smoothedNosePos - m_lastNosePos;
        float movementDist = glm::length(delta);

        if (movementDist < m_noseDeadZone) {
            return;  // Movement too small, ignore
        }

        // Estimate velocity for trajectory preview (Phase 2)
        if (m_trajectoryPreviewEnabled && movementDist > 2.0f) {
            float speed = movementDist / elapsed;
            speed = glm::clamp(speed * 1.5f * m_noseSensitivity, 200.0f, 4000.0f);

            glm::vec2 dir = glm::normalize(delta);
            glm::vec2 velocity = dir * speed;

            velocity.y = glm::clamp(velocity.y, 600.0f, 2500.0f);
            velocity.x = glm::clamp(velocity.x, -800.0f, 800.0f);

            // Calculate trajectory preview
            calculateTrajectory(velocity);
        }

        // Check for upward throw gesture
        if (delta.y > 15.0f) {
            float speed = movementDist / elapsed;
            // Reduced from 2.0x to 1.5x for better control, apply sensitivity
            speed = glm::clamp(speed * 1.5f * m_noseSensitivity, 200.0f, 4000.0f);

            glm::vec2 dir = glm::normalize(delta);
            glm::vec2 velocity = dir * speed;

            velocity.y = glm::clamp(velocity.y, 600.0f, 2500.0f);
            velocity.x = glm::clamp(velocity.x, -800.0f, 800.0f);

            launchBall(velocity.x, velocity.y);
        }
        else if (elapsed > 0.1f) {
            m_lastNosePos = m_smoothedNosePos;
            m_lastNoseTime = m_currentTime;
        }
    }
}

} // namespace Game
