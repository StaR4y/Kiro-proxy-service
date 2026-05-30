package xyz.star4y.kiroproxy.admin;

final class AdminUserMapper {

    private AdminUserMapper() {
    }

    static AdminDtos.AdminUserResponse toResponse(AdminUserEntity entity) {
        return new AdminDtos.AdminUserResponse(
            entity.getUserId(),
            entity.getUsername(),
            entity.getDisplayName(),
            entity.getRole(),
            entity.getEnabled(),
            entity.getFirstLogin(),
            entity.getLastLoginAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
