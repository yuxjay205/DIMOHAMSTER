#pragma once

#include <glm/glm.hpp>
#include <glm/gtc/matrix_transform.hpp>
#include <glm/gtc/type_ptr.hpp>

namespace Engine {

enum class ProjectionType {
    Perspective,
    Orthographic
};

class Camera {
public:
    Camera();
    ~Camera() = default;

    // Initialization
    void init(float width, float height, ProjectionType type = ProjectionType::Perspective);
    void resize(float width, float height);

    // Camera transformations
    void setPosition(const glm::vec3& position);
    void setRotation(float pitch, float yaw, float roll = 0.0f);
    void lookAt(const glm::vec3& target);

    // Movement
    void translate(const glm::vec3& delta);
    void rotate(float deltaPitch, float deltaYaw);

    // Perspective settings
    void setFov(float fov);
    void setNearFar(float near, float far);

    // Orthographic settings
    void setOrthoSize(float size);

    // Matrix accessors
    const glm::mat4& getViewMatrix() const { return m_viewMatrix; }
    const glm::mat4& getProjectionMatrix() const { return m_projectionMatrix; }
    glm::mat4 getViewProjectionMatrix() const { return m_projectionMatrix * m_viewMatrix; }

    // Position and orientation accessors
    const glm::vec3& getPosition() const { return m_position; }
    const glm::vec3& getForward() const { return m_forward; }
    const glm::vec3& getRight() const { return m_right; }
    const glm::vec3& getUp() const { return m_up; }

    float getPitch() const { return m_pitch; }
    float getYaw() const { return m_yaw; }

    // For spatial audio listener
    void getListenerAttributes(glm::vec3& position, glm::vec3& velocity,
                               glm::vec3& forward, glm::vec3& up) const;

private:
    void updateViewMatrix();
    void updateProjectionMatrix();
    void updateVectors();

    // Matrices
    glm::mat4 m_viewMatrix;
    glm::mat4 m_projectionMatrix;

    // Transform
    glm::vec3 m_position;
    glm::vec3 m_forward;
    glm::vec3 m_up;
    glm::vec3 m_right;
    glm::vec3 m_worldUp;

    // For velocity calculation (audio)
    glm::vec3 m_lastPosition;
    glm::vec3 m_velocity;

    // Euler angles (in degrees)
    float m_pitch;
    float m_yaw;
    float m_roll;

    // Projection settings
    ProjectionType m_projectionType;
    float m_fov;
    float m_aspectRatio;
    float m_nearPlane;
    float m_farPlane;
    float m_orthoSize;
    float m_width;
    float m_height;
};

} // namespace Engine
