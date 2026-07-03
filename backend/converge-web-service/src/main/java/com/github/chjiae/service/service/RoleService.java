package com.github.chjiae.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.chjiae.common.exception.BusinessException;
import com.github.chjiae.service.dto.role.CreateRoleRequest;
import com.github.chjiae.service.dto.role.RoleResponse;
import com.github.chjiae.service.entity.Role;
import com.github.chjiae.service.entity.RolePermission;
import com.github.chjiae.service.mapper.RoleMapper;
import com.github.chjiae.service.mapper.RolePermissionMapper;
import com.github.chjiae.service.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色权限管理服务，提供角色的 CRUD 和权限分配功能。
 * 查询角色时同时返回系统角色（tenantId=null）和当前租户的角色。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    /** 角色数据访问层 */
    private final RoleMapper roleMapper;

    /** 角色权限关联数据访问层 */
    private final RolePermissionMapper rolePermissionMapper;

    /**
     * 查询当前租户的角色列表（包含系统角色）
     * 使用忽略租户过滤，手动查询 tenantId IS NULL（系统角色）或 tenantId = 当前租户。
     *
     * @return 角色响应列表
     */
    public List<RoleResponse> listRoles() {
        log.info("查询角色列表，当前租户 ID: {}", TenantContext.getTenantId());

        // 忽略多租户过滤，手动构建查询条件
        TenantContext.setIgnoreTenant(true);

        Long currentTenantId = TenantContext.getTenantId();
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>()
                        .isNull(Role::getTenantId)  // 系统角色
                        .or()
                        .eq(Role::getTenantId, currentTenantId)  // 当前租户角色
                        .orderByAsc(Role::getCreatedAt)
        );

        return roles.stream()
                .map(this::toRoleResponse)
                .collect(Collectors.toList());
    }

    /**
     * 创建租户自定义角色
     *
     * @param request 创建角色请求参数
     * @return 角色响应
     */
    public RoleResponse createRole(CreateRoleRequest request) {
        Long currentTenantId = TenantContext.getTenantId();
        log.info("创建角色请求，编码: {}，名称: {}，租户 ID: {}", request.getCode(), request.getName(), currentTenantId);

        // 检查角色编码在当前租户内是否唯一（包括系统角色中相同编码的检查）
        TenantContext.setIgnoreTenant(true);
        Long codeCount = roleMapper.selectCount(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getCode, request.getCode())
                        .and(w -> w
                                .isNull(Role::getTenantId)
                                .or()
                                .eq(Role::getTenantId, currentTenantId)
                        )
        );
        if (codeCount > 0) {
            log.warn("创建角色失败，编码已存在: {}", request.getCode());
            throw new BusinessException(400, "角色编码已存在");
        }

        // 创建角色，tenantId 由多租户拦截器自动注入（但这里我们已忽略过滤，需手动设置）
        Role role = new Role();
        role.setTenantId(currentTenantId);
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setIsSystem(false);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.insert(role);

        log.info("角色创建成功，角色 ID: {}，编码: {}", role.getId(), role.getCode());
        return toRoleResponse(role);
    }

    /**
     * 更新角色信息（系统角色不可修改）
     *
     * @param id      角色 ID
     * @param request 更新请求参数
     * @return 更新后的角色响应
     */
    public RoleResponse updateRole(Long id, CreateRoleRequest request) {
        log.info("更新角色，角色 ID: {}", id);

        // 忽略多租户过滤以查询角色
        TenantContext.setIgnoreTenant(true);

        Role role = roleMapper.selectById(id);
        if (role == null) {
            log.warn("更新角色失败，角色不存在: {}", id);
            throw new BusinessException(404, "角色不存在");
        }

        // 系统角色不可修改
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            log.warn("更新角色失败，系统角色不可修改: {}", role.getCode());
            throw new BusinessException(400, "系统内置角色不可修改");
        }

        // 更新角色信息
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);

        log.info("角色更新成功，角色 ID: {}", id);
        return toRoleResponse(role);
    }

    /**
     * 删除角色（系统角色不可删除）
     *
     * @param id 角色 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        log.info("删除角色，角色 ID: {}", id);

        // 忽略多租户过滤以查询角色
        TenantContext.setIgnoreTenant(true);

        Role role = roleMapper.selectById(id);
        if (role == null) {
            log.warn("删除角色失败，角色不存在: {}", id);
            throw new BusinessException(404, "角色不存在");
        }

        // 系统角色不可删除
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            log.warn("删除角色失败，系统角色不可删除: {}", role.getCode());
            throw new BusinessException(400, "系统内置角色不可删除");
        }

        // 删除角色的权限关联
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        );

        // 删除角色
        roleMapper.deleteById(id);
        log.info("角色删除成功，角色 ID: {}", id);
    }

    /**
     * 为角色配置权限（先删旧关联，再加新关联）
     *
     * @param roleId        角色 ID
     * @param permissionIds 权限 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        log.info("配置角色权限，角色 ID: {}，权限数量: {}", roleId, permissionIds != null ? permissionIds.size() : 0);

        // 忽略多租户过滤以查询角色
        TenantContext.setIgnoreTenant(true);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            log.warn("配置权限失败，角色不存在: {}", roleId);
            throw new BusinessException(404, "角色不存在");
        }

        // 删除旧的权限关联
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId)
        );

        // 添加新的权限关联
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermissionId(permissionId);
                rolePermissionMapper.insert(rolePermission);
            }
        }

        log.info("角色权限配置成功，角色 ID: {}，权限数量: {}", roleId, permissionIds != null ? permissionIds.size() : 0);
    }

    /**
     * 将角色实体转换为响应 DTO
     *
     * @param role 角色实体
     * @return 角色响应 DTO
     */
    private RoleResponse toRoleResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .tenantId(role.getTenantId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .createdAt(role.getCreatedAt())
                .build();
    }
}
