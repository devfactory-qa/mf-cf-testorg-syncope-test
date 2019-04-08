/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.provisioning.java.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.lib.AnyOperations;
import org.apache.syncope.common.lib.patch.AnyPatch;
import org.apache.syncope.common.lib.policy.PasswordRuleConf;
import org.apache.syncope.common.lib.to.AnyObjectTO;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.to.AttrTO;
import org.apache.syncope.common.lib.to.ConnObjectTO;
import org.apache.syncope.common.lib.to.GroupTO;
import org.apache.syncope.common.lib.to.RealmTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.core.persistence.api.dao.ExternalResourceDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.AnyUtils;
import org.apache.syncope.core.persistence.api.entity.resource.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.persistence.api.dao.RealmDAO;
import org.apache.syncope.core.persistence.api.entity.Realm;
import org.apache.syncope.core.persistence.api.entity.resource.Item;
import org.apache.syncope.core.persistence.api.entity.resource.OrgUnit;
import org.apache.syncope.core.persistence.api.entity.resource.Provision;
import org.apache.syncope.core.persistence.api.entity.task.PullTask;
import org.apache.syncope.core.provisioning.api.MappingManager;
import org.apache.syncope.core.spring.policy.InvalidPasswordRuleConf;
import org.apache.syncope.core.spring.security.Encryptor;
import org.apache.syncope.core.spring.security.PasswordGenerator;
import org.apache.syncope.core.spring.security.SecureRandomUtils;
import org.identityconnectors.common.Base64;
import org.identityconnectors.common.security.GuardedByteArray;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConnObjectUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ConnObjectUtils.class);

    private static final Encryptor ENCRYPTOR = Encryptor.getInstance();

    @Autowired
    private TemplateUtils templateUtils;

    @Autowired
    private RealmDAO realmDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private ExternalResourceDAO resourceDAO;

    @Autowired
    private PasswordGenerator passwordGenerator;

    @Autowired
    private MappingManager mappingManager;

    /**
     * Extract password value from passed value (if instance of GuardedString or GuardedByteArray).
     *
     * @param pwd received from the underlying connector
     * @return password value
     */
    public static String getPassword(final Object pwd) {
        final StringBuilder result = new StringBuilder();

        if (pwd instanceof GuardedString) {
            result.append(SecurityUtil.decrypt((GuardedString) pwd));
        } else if (pwd instanceof GuardedByteArray) {
            result.append(SecurityUtil.decrypt((GuardedByteArray) pwd));
        } else if (pwd instanceof String) {
            result.append((String) pwd);
        } else {
            result.append(pwd.toString());
        }

        return result.toString();
    }

    /**
     * Builds {@link ConnObjectTO} out of {@link ConnectorObject}.
     *
     * @param connObject connector object.
     * @return transfer object
     */
    public static ConnObjectTO getConnObjectTO(final ConnectorObject connObject) {
        return connObject == null ? new ConnObjectTO() : getConnObjectTO(connObject.getAttributes());
    }

    /**
     * Builds {@link ConnObjectTO} out of a collection of {@link Attribute} instances.
     *
     * @param attrs attributes
     * @return transfer object
     */
    public static ConnObjectTO getConnObjectTO(final Set<Attribute> attrs) {
        final ConnObjectTO connObjectTO = new ConnObjectTO();

        if (attrs != null) {
            for (Attribute attr : attrs) {
                AttrTO attrTO = new AttrTO();
                attrTO.setSchema(attr.getName());

                if (attr.getValue() != null) {
                    for (Object value : attr.getValue()) {
                        if (value != null) {
                            if (value instanceof GuardedString || value instanceof GuardedByteArray) {
                                attrTO.getValues().add(getPassword(value));
                            } else if (value instanceof byte[]) {
                                attrTO.getValues().add(Base64.encode((byte[]) value));
                            } else {
                                attrTO.getValues().add(value.toString());
                            }
                        }
                    }
                }

                connObjectTO.getAttrs().add(attrTO);
            }
        }

        return connObjectTO;
    }

    /**
     * Build a UserTO / GroupTO / AnyObjectTO out of connector object attributes and schema mapping.
     *
     * @param obj connector object
     * @param pullTask pull task
     * @param provision provision information
     * @param anyUtils utils
     * @param <T> any object
     * @return UserTO for the user to be created
     */
    @Transactional(readOnly = true)
    public <T extends AnyTO> T getAnyTO(
            final ConnectorObject obj,
            final PullTask pullTask,
            final Provision provision,
            final AnyUtils anyUtils) {

        T anyTO = getAnyTOFromConnObject(obj, pullTask, provision, anyUtils);

        // (for users) if password was not set above, generate if resource is configured for that
        if (anyTO instanceof UserTO
                && StringUtils.isBlank(((UserTO) anyTO).getPassword())
                && provision.getResource().isRandomPwdIfNotProvided()) {

            UserTO userTO = (UserTO) anyTO;

            List<PasswordRuleConf> ruleConfs = new ArrayList<>();

            Realm realm = realmDAO.findByFullPath(userTO.getRealm());
            if (realm != null) {
                for (Realm ancestor : realmDAO.findAncestors(realm)) {
                    if (ancestor.getPasswordPolicy() != null) {
                        ruleConfs.addAll(ancestor.getPasswordPolicy().getRuleConfs());
                    }
                }
            }

            for (String resName : userTO.getResources()) {
                ExternalResource resource = resourceDAO.find(resName);
                if (resource != null && resource.getPasswordPolicy() != null) {
                    ruleConfs.addAll(resource.getPasswordPolicy().getRuleConfs());
                }
            }

            String password;
            try {
                password = passwordGenerator.generate(ruleConfs);
            } catch (InvalidPasswordRuleConf e) {
                LOG.error("Could not generate policy-compliant random password for {}", userTO, e);

                password = SecureRandomUtils.generateRandomPassword(16);
            }
            userTO.setPassword(password);
        }

        return anyTO;
    }

    public RealmTO getRealmTO(final ConnectorObject obj, final PullTask task, final OrgUnit orgUnit) {
        RealmTO realmTO = new RealmTO();

        for (Item item : MappingUtils.getPullItems(orgUnit.getItems())) {
            mappingManager.setIntValues(item, obj.getAttributeByName(item.getExtAttrName()), realmTO);
        }

        return realmTO;
    }

    /**
     * Build {@link AnyPatch} out of connector object attributes and schema mapping.
     *
     * @param key any object to be updated
     * @param obj connector object
     * @param original any object to get diff from
     * @param pullTask pull task
     * @param provision provision information
     * @param anyUtils utils
     * @param <T> any object
     * @return modifications for the any object to be updated
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public <T extends AnyPatch> T getAnyPatch(
            final String key,
            final ConnectorObject obj,
            final AnyTO original,
            final PullTask pullTask,
            final Provision provision,
            final AnyUtils anyUtils) {

        AnyTO updated = getAnyTOFromConnObject(obj, pullTask, provision, anyUtils);
        updated.setKey(key);

        T anyPatch = null;
        if (null != anyUtils.anyTypeKind()) {
            switch (anyUtils.anyTypeKind()) {
                case USER:
                    UserTO originalUser = (UserTO) original;
                    UserTO updatedUser = (UserTO) updated;

                    if (StringUtils.isBlank(updatedUser.getUsername())) {
                        updatedUser.setUsername(originalUser.getUsername());
                    }

                    // update password if and only if password is really changed
                    User user = userDAO.authFind(key);
                    if (StringUtils.isBlank(updatedUser.getPassword())
                            || ENCRYPTOR.verify(updatedUser.getPassword(),
                                    user.getCipherAlgorithm(), user.getPassword())) {

                        updatedUser.setPassword(null);
                    }

                    updatedUser.setSecurityQuestion(originalUser.getSecurityQuestion());

                    if (!mappingManager.hasMustChangePassword(provision)) {
                        updatedUser.setMustChangePassword(originalUser.isMustChangePassword());
                    }

                    anyPatch = (T) AnyOperations.diff(updatedUser, originalUser, true);
                    break;

                case GROUP:
                    GroupTO originalGroup = (GroupTO) original;
                    GroupTO updatedGroup = (GroupTO) updated;

                    if (StringUtils.isBlank(updatedGroup.getName())) {
                        updatedGroup.setName(originalGroup.getName());
                    }
                    updatedGroup.setUserOwner(originalGroup.getUserOwner());
                    updatedGroup.setGroupOwner(originalGroup.getGroupOwner());
                    updatedGroup.setUDynMembershipCond(originalGroup.getUDynMembershipCond());
                    updatedGroup.getADynMembershipConds().putAll(originalGroup.getADynMembershipConds());
                    updatedGroup.getTypeExtensions().addAll(originalGroup.getTypeExtensions());

                    anyPatch = (T) AnyOperations.diff(updatedGroup, originalGroup, true);
                    break;

                case ANY_OBJECT:
                    AnyObjectTO originalAnyObject = (AnyObjectTO) original;
                    AnyObjectTO updatedAnyObject = (AnyObjectTO) updated;

                    if (StringUtils.isBlank(updatedAnyObject.getName())) {
                        updatedAnyObject.setName(originalAnyObject.getName());
                    }

                    anyPatch = (T) AnyOperations.diff(updatedAnyObject, originalAnyObject, true);
                    break;

                default:
            }
        }
        // SYNCOPE-1343, remove null or empty values from the patch plain attributes
        if (anyPatch != null) {
            AnyOperations.cleanEmptyAttrs(updated, anyPatch);
        }
        return anyPatch;
    }

    private <T extends AnyTO> T getAnyTOFromConnObject(
            final ConnectorObject obj,
            final PullTask pullTask,
            final Provision provision,
            final AnyUtils anyUtils) {

        T anyTO = anyUtils.newAnyTO();
        anyTO.setType(provision.getAnyType().getKey());

        // 1. fill with data from connector object
        anyTO.setRealm(pullTask.getDestinatioRealm().getFullPath());
        for (Item item : MappingUtils.getPullItems(provision.getMapping().getItems())) {
            mappingManager.setIntValues(item, obj.getAttributeByName(item.getExtAttrName()), anyTO);
        }

        // 2. add data from defined template (if any)
        templateUtils.apply(anyTO, pullTask.getTemplate(provision.getAnyType()));

        return anyTO;
    }
}
