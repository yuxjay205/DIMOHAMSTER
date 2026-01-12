#include "Camera.h"
#include <cmath>

namespace Engine {

Camera::Camera()
    : m_viewMatrix(1.0f)
    , m_projectionMatrix(1.0f)
    , m_position(0.0f, 0.0f, 5.0f)
    , m_forward(0.0f, 0.0f, -1.0f)
    , m_up(0.0f, 1.0f, 0.0f)
    , m_right(1.0f, 0.0f, 0.0f)
    , m_worldUp(0.0f, 1.0f, 0.0f)
    , m_lastPosition(0.0f, 0.0f, 5.0f)
    , m_velocity(0.0f)
    , m_pitch(0.0f)
    , m_yaw(-90.0f)
    , m_roll(0.0f)
    , m_projectionType(ProjectionType::Perspective)
    , m_fov(45.0f)
    , m_aspectRatio(16.0f / 9.0f)
    , m_nearPlane(0.1f)
    , m_farPlane(1000.0f)
    , m_orthoSize(10.0f)
    , m_width(1920.0f)
    , m_height(1080.0f) {
    updateVectors();
    updateViewMatrix();
    updateProjectionMatrix();
}

void Camera::init(float width, float height, ProjectionType type) {
    m_width = width;
    m_height = height;
    m_aspectRatio = width / height;
    m_projectionType = type;

    updateVectors();
    updateViewMatrix();
    updateProjectionMatrix();
}

void Camera::resize(float width, float height) {
    m_width = width;
    m_height = height;
    m_aspectRatio = width / height;
    updateProjectionMatrix();
}

void Camera::setPosition(const glm::vec3& position) {
    m_lastPosition = m_position;
    m_position = position;
    m_velocity = m_position - m_lastPosition;
    updateViewMatrix();
}

void Camera::setRotation(float pitch, float yaw, float roll) {
    m_pitch = pitch;
    m_yaw = yaw;
    m_roll = roll;

    // Constrain pitch
    if (m_pitch > 89.0f) m_pitch = 89.0f;
    if (m_pitch < -89.0f) m_pitch = -89.0f;

    updateVectors();
    updateViewMatrix();
}

void Camera::lookAt(const glm::vec3& target) {
    glm::vec3 direction = glm::normalize(target - m_position);

    m_pitch = glm::degrees(asin(direction.y));
    m_yaw = glm::degrees(atan2(direction.z, direction.x));

    updateVectors();
    updateViewMatrix();
}

void Camera::translate(const glm::vec3& delta) {
    m_lastPosition = m_position;
    m_position += m_right * delta.x;
    m_position += m_up * delta.y;
    m_position += m_forward * delta.z;
    m_velocity = m_position - m_lastPosition;
    updateViewMatrix();
}

void Camera::rotate(float deltaPitch, float deltaYaw) {
    m_pitch += deltaPitch;
    m_yaw += deltaYaw;

    // Constrain pitch
    if (m_pitch > 89.0f) m_pitch = 89.0f;
    if (m_pitch < -89.0f) m_pitch = -89.0f;

    updateVectors();
    updateViewMatrix();
}

void Camera::setFov(float fov) {
    m_fov = fov;
    if (m_projectionType == ProjectionType::Perspective) {
        updateProjectionMatrix();
    }
}

void Camera::setNearFar(float near, float far) {
    m_nearPlane = near;
    m_farPlane = far;
    updateProjectionMatrix();
}

void Camera::setOrthoSize(float size) {
    m_orthoSize = size;
    if (m_projectionType == ProjectionType::Orthographic) {
        updateProjectionMatrix();
    }
}

void Camera::getListenerAttributes(glm::vec3& position, glm::vec3& velocity,
                                    glm::vec3& forward, glm::vec3& up) const {
    position = m_position;
    velocity = m_velocity;
    forward = m_forward;
    up = m_up;
}

void Camera::updateViewMatrix() {
    m_viewMatrix = glm::lookAt(m_position, m_position + m_forward, m_up);
}

void Camera::updateProjectionMatrix() {
    if (m_projectionType == ProjectionType::Perspective) {
        m_projectionMatrix = glm::perspective(
            glm::radians(m_fov),
            m_aspectRatio,
            m_nearPlane,
            m_farPlane
        );
    } else {
        float halfHeight = m_orthoSize * 0.5f;
        float halfWidth = halfHeight * m_aspectRatio;
        m_projectionMatrix = glm::ortho(
            -halfWidth, halfWidth,
            -halfHeight, halfHeight,
            m_nearPlane, m_farPlane
        );
    }
}

void Camera::updateVectors() {
    // Calculate forward vector from euler angles
    glm::vec3 front;
    front.x = cos(glm::radians(m_yaw)) * cos(glm::radians(m_pitch));
    front.y = sin(glm::radians(m_pitch));
    front.z = sin(glm::radians(m_yaw)) * cos(glm::radians(m_pitch));
    m_forward = glm::normalize(front);

    // Recalculate right and up vectors
    m_right = glm::normalize(glm::cross(m_forward, m_worldUp));
    m_up = glm::normalize(glm::cross(m_right, m_forward));

    // Apply roll if needed
    if (m_roll != 0.0f) {
        glm::mat4 rollMatrix = glm::rotate(glm::mat4(1.0f), glm::radians(m_roll), m_forward);
        m_up = glm::vec3(rollMatrix * glm::vec4(m_up, 0.0f));
        m_right = glm::vec3(rollMatrix * glm::vec4(m_right, 0.0f));
    }
}

} // namespace Engine
