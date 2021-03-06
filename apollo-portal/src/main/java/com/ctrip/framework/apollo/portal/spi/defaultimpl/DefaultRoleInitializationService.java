package com.ctrip.framework.apollo.portal.spi.defaultimpl;

import com.ctrip.framework.apollo.common.entity.App;
import com.ctrip.framework.apollo.common.entity.BaseEntity;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.portal.constant.PermissionType;
import com.ctrip.framework.apollo.portal.constant.RoleType;
import com.ctrip.framework.apollo.portal.entity.po.Permission;
import com.ctrip.framework.apollo.portal.entity.po.Role;
import com.ctrip.framework.apollo.portal.service.RoleInitializationService;
import com.ctrip.framework.apollo.portal.service.RolePermissionService;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.portal.util.RoleUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by timothy on 2017/4/26.
 */
public class DefaultRoleInitializationService implements RoleInitializationService {

    @Autowired
    private UserInfoHolder userInfoHolder;
    @Autowired
    private RolePermissionService rolePermissionService;

    @Override
    @Transactional
    public void initAppRoles(App app) {
        String appId = app.getAppId();

        // 创建 App 拥有者的角色名
        String appMasterRoleName = RoleUtils.buildAppMasterRoleName(appId);
        // has created before
        // 校验角色是否已经存在。若是，直接返回
        if (rolePermissionService.findRoleByRoleName(appMasterRoleName) != null) {
            return;
        }
        String operator = app.getDataChangeCreatedBy();
        //create app permissions
        // 创建 App 角色
        createAppMasterRole(appId, operator);
        // 授权 Role 给 App 拥有者
        // assign master role to user
        rolePermissionService.assignRoleToUsers(RoleUtils.buildAppMasterRoleName(appId), Sets.newHashSet(app.getOwnerName()), operator);

        // 初始化 Namespace 角色
        initNamespaceRoles(appId, ConfigConsts.NAMESPACE_APPLICATION, operator);
        // 授权 Role 给 App 创建者
        //assign modify、release namespace role to user
        rolePermissionService.assignRoleToUsers(RoleUtils.buildNamespaceRoleName(appId, ConfigConsts.NAMESPACE_APPLICATION, RoleType.MODIFY_NAMESPACE), Sets.newHashSet(operator), operator);
        rolePermissionService.assignRoleToUsers(RoleUtils.buildNamespaceRoleName(appId, ConfigConsts.NAMESPACE_APPLICATION, RoleType.RELEASE_NAMESPACE), Sets.newHashSet(operator), operator);
    }

    @Override
    @Transactional
    public void initNamespaceRoles(String appId, String namespaceName, String operator) {
        // 创建 Namespace 修改的角色名
        String modifyNamespaceRoleName = RoleUtils.buildModifyNamespaceRoleName(appId, namespaceName);
        // 若不存在对应的 Role ，进行创建
        if (rolePermissionService.findRoleByRoleName(modifyNamespaceRoleName) == null) {
            createNamespaceRole(appId, namespaceName, PermissionType.MODIFY_NAMESPACE, RoleUtils.buildModifyNamespaceRoleName(appId, namespaceName), operator);
        }

        // 创建 Namespace 发布的角色名
        String releaseNamespaceRoleName = RoleUtils.buildReleaseNamespaceRoleName(appId, namespaceName);
        // 若不存在对应的 Role ，进行创建
        if (rolePermissionService.findRoleByRoleName(releaseNamespaceRoleName) == null) {
            createNamespaceRole(appId, namespaceName, PermissionType.RELEASE_NAMESPACE,
                    RoleUtils.buildReleaseNamespaceRoleName(appId, namespaceName), operator);
        }
    }

    private void createAppMasterRole(String appId, String operator) {
        // 创建 App 对应的 Permission 集合，并保存到数据库
        Set<Permission> appPermissions = Lists.newArrayList(PermissionType.CREATE_CLUSTER, PermissionType.CREATE_NAMESPACE, PermissionType.ASSIGN_ROLE)
                .stream().map(permissionType -> createPermission(appId, permissionType, operator) /* 创建 Permission 对象 */ ).collect(Collectors.toSet());
        Set<Permission> createdAppPermissions = rolePermissionService.createPermissions(appPermissions);
        Set<Long> appPermissionIds = createdAppPermissions.stream().map(BaseEntity::getId).collect(Collectors.toSet());

        // 创建 App 对应的 Role 对象，并保存到数据库
        // create app master role
        Role appMasterRole = createRole(RoleUtils.buildAppMasterRoleName(appId), operator);
        rolePermissionService.createRoleWithPermissions(appMasterRole, appPermissionIds);
    }

    private Permission createPermission(String targetId, String permissionType, String operator) {
        Permission permission = new Permission();
        permission.setPermissionType(permissionType);
        permission.setTargetId(targetId);
        permission.setDataChangeCreatedBy(operator);
        permission.setDataChangeLastModifiedBy(operator);
        return permission;
    }

    private Role createRole(String roleName, String operator) {
        Role role = new Role();
        role.setRoleName(roleName);
        role.setDataChangeCreatedBy(operator);
        role.setDataChangeLastModifiedBy(operator);
        return role;
    }

    private void createNamespaceRole(String appId, String namespaceName, String permissionType,
                                     String roleName, String operator) {
        // 创建 Namespace 对应的 Permission 对象，并保存到数据库
        Permission permission = createPermission(RoleUtils.buildNamespaceTargetId(appId, namespaceName), permissionType, operator);
        Permission createdPermission = rolePermissionService.createPermission(permission);

        // 创建 Namespace 对应的 Role 对象，并保存到数据库
        Role role = createRole(roleName, operator);
        rolePermissionService.createRoleWithPermissions(role, Sets.newHashSet(createdPermission.getId()));
    }

}
