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
package org.apache.syncope.fit.core.reference;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.syncope.common.lib.policy.AccountRuleConf;
import org.apache.syncope.common.lib.policy.DefaultAccountRuleConf;
import org.apache.syncope.common.lib.policy.DefaultPasswordRuleConf;
import org.apache.syncope.common.lib.policy.HaveIBeenPwnedPasswordRuleConf;
import org.apache.syncope.common.lib.policy.PasswordRuleConf;
import org.apache.syncope.common.lib.report.AuditReportletConf;
import org.apache.syncope.common.lib.report.GroupReportletConf;
import org.apache.syncope.common.lib.report.ReconciliationReportletConf;
import org.apache.syncope.common.lib.report.ReportletConf;
import org.apache.syncope.common.lib.report.StaticReportletConf;
import org.apache.syncope.common.lib.report.UserReportletConf;
import org.apache.syncope.common.lib.to.SchedTaskTO;
import org.apache.syncope.common.lib.types.TaskType;
import org.apache.syncope.core.logic.TaskLogic;
import org.apache.syncope.core.provisioning.java.job.report.AuditReportlet;
import org.apache.syncope.core.provisioning.java.job.report.GroupReportlet;
import org.apache.syncope.core.provisioning.java.job.report.ReconciliationReportlet;
import org.apache.syncope.core.provisioning.java.job.report.StaticReportlet;
import org.apache.syncope.core.provisioning.java.job.report.UserReportlet;
import org.apache.syncope.core.migration.MigrationPullActions;
import org.apache.syncope.core.persistence.api.DomainsHolder;
import org.apache.syncope.core.persistence.api.ImplementationLookup;
import org.apache.syncope.core.persistence.api.dao.AccountRule;
import org.apache.syncope.core.persistence.api.dao.AnySearchDAO;
import org.apache.syncope.core.persistence.api.dao.PasswordRule;
import org.apache.syncope.core.persistence.api.dao.Reportlet;
import org.apache.syncope.core.persistence.jpa.attrvalue.validation.AlwaysTrueValidator;
import org.apache.syncope.core.persistence.jpa.attrvalue.validation.BasicValidator;
import org.apache.syncope.core.persistence.jpa.attrvalue.validation.BinaryValidator;
import org.apache.syncope.core.persistence.jpa.attrvalue.validation.EmailAddressValidator;
import org.apache.syncope.core.provisioning.java.DefaultLogicActions;
import org.apache.syncope.core.provisioning.java.data.DefaultItemTransformer;
import org.apache.syncope.core.provisioning.java.propagation.AzurePropagationActions;
import org.apache.syncope.core.provisioning.java.propagation.DBPasswordPropagationActions;
import org.apache.syncope.core.provisioning.java.propagation.GoogleAppsPropagationActions;
import org.apache.syncope.core.provisioning.java.propagation.LDAPMembershipPropagationActions;
import org.apache.syncope.core.provisioning.java.propagation.LDAPPasswordPropagationActions;
import org.apache.syncope.core.provisioning.java.propagation.SCIMv11PropagationActions;
import org.apache.syncope.core.provisioning.java.pushpull.DBPasswordPullActions;
import org.apache.syncope.core.provisioning.java.pushpull.LDAPMembershipPullActions;
import org.apache.syncope.core.provisioning.java.pushpull.LDAPPasswordPullActions;
import org.apache.syncope.core.spring.policy.DefaultAccountRule;
import org.apache.syncope.core.spring.policy.DefaultPasswordRule;
import org.apache.syncope.core.spring.policy.HaveIBeenPwnedPasswordRule;
import org.apache.syncope.core.spring.security.AuthContextUtils;
import org.apache.syncope.core.spring.security.SyncopeJWTSSOProvider;
import org.apache.syncope.core.workflow.api.UserWorkflowAdapter;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Static implementation providing information about the integration test environment.
 */
public class ITImplementationLookup implements ImplementationLookup {

    private static final Map<Type, Set<String>> CLASS_NAMES = new HashMap<Type, Set<String>>() {

        private static final long serialVersionUID = 3109256773218160485L;

        {
            Set<String> classNames = new HashSet<>();
            classNames.add(SyncopeJWTSSOProvider.class.getName());
            classNames.add(CustomJWTSSOProvider.class.getName());
            put(Type.JWT_SSO_PROVIDER, classNames);

            classNames = new HashSet<>();
            classNames.add(ReconciliationReportletConf.class.getName());
            classNames.add(UserReportletConf.class.getName());
            classNames.add(GroupReportletConf.class.getName());
            classNames.add(AuditReportletConf.class.getName());
            classNames.add(StaticReportletConf.class.getName());
            put(Type.REPORTLET_CONF, classNames);

            classNames = new HashSet<>();
            classNames.add(TestAccountRuleConf.class.getName());
            classNames.add(DefaultAccountRuleConf.class.getName());
            put(Type.ACCOUNT_RULE_CONF, classNames);

            classNames = new HashSet<>();
            classNames.add(TestPasswordRuleConf.class.getName());
            classNames.add(DefaultPasswordRuleConf.class.getName());
            classNames.add(HaveIBeenPwnedPasswordRuleConf.class.getName());
            put(Type.PASSWORD_RULE_CONF, classNames);

            classNames = new HashSet<>();
            classNames.add(DateToDateItemTransformer.class.getName());
            classNames.add(DateToLongItemTransformer.class.getName());
            classNames.add(PrefixItemTransformer.class.getName());
            classNames.add(DefaultItemTransformer.class.getName());
            put(Type.ITEM_TRANSFORMER, classNames);

            classNames = new HashSet<>();
            classNames.add(TestSampleJobDelegate.class.getName());
            put(Type.TASKJOBDELEGATE, classNames);

            classNames = new HashSet<>();
            classNames.add(TestReconciliationFilterBuilder.class.getName());
            put(Type.RECONCILIATION_FILTER_BUILDER, classNames);

            classNames = new HashSet<>();
            classNames.add(DoubleValueLogicActions.class.getName());
            classNames.add(DefaultLogicActions.class.getName());
            put(Type.LOGIC_ACTIONS, classNames);

            classNames = new HashSet<>();
            classNames.add(LDAPMembershipPropagationActions.class.getName());
            classNames.add(LDAPPasswordPropagationActions.class.getName());
            classNames.add(DBPasswordPropagationActions.class.getName());
            classNames.add(AzurePropagationActions.class.getName());
            classNames.add(SCIMv11PropagationActions.class.getName());
            classNames.add(GoogleAppsPropagationActions.class.getName());
            put(Type.PROPAGATION_ACTIONS, classNames);

            classNames = new HashSet<>();
            classNames.add(LDAPPasswordPullActions.class.getName());
            classNames.add(TestPullActions.class.getName());
            classNames.add(MigrationPullActions.class.getName());
            classNames.add(LDAPMembershipPullActions.class.getName());
            classNames.add(DBPasswordPullActions.class.getName());
            put(Type.PULL_ACTIONS, classNames);

            classNames = new HashSet<>();
            put(Type.PUSH_ACTIONS, classNames);

            classNames = new HashSet<>();
            classNames.add(TestPullRule.class.getName());
            put(Type.PULL_CORRELATION_RULE, classNames);

            classNames = new HashSet<>();
            classNames.add(BasicValidator.class.getName());
            classNames.add(EmailAddressValidator.class.getName());
            classNames.add(AlwaysTrueValidator.class.getName());
            classNames.add(BinaryValidator.class.getName());
            put(Type.VALIDATOR, classNames);

            classNames = new HashSet<>();
            classNames.add(TestNotificationRecipientsProvider.class.getName());
            put(Type.NOTIFICATION_RECIPIENTS_PROVIDER, classNames);

            classNames = new HashSet<>();
            classNames.add(TestFileRewriteAuditAppender.class.getName());
            classNames.add(TestFileAuditAppender.class.getName());
            put(Type.AUDIT_APPENDER, classNames);
        }
    };

    private static final Map<Class<? extends ReportletConf>, Class<? extends Reportlet>> REPORTLET_CLASSES =
            new HashMap<Class<? extends ReportletConf>, Class<? extends Reportlet>>() {

        private static final long serialVersionUID = 3109256773218160485L;

        {
            put(AuditReportletConf.class, AuditReportlet.class);
            put(ReconciliationReportletConf.class, ReconciliationReportlet.class);
            put(GroupReportletConf.class, GroupReportlet.class);
            put(UserReportletConf.class, UserReportlet.class);
            put(StaticReportletConf.class, StaticReportlet.class);
        }
    };

    private static final Map<Class<? extends AccountRuleConf>, Class<? extends AccountRule>> ACCOUNT_RULE_CLASSES =
            new HashMap<Class<? extends AccountRuleConf>, Class<? extends AccountRule>>() {

        private static final long serialVersionUID = 3109256773218160485L;

        {
            put(TestAccountRuleConf.class, TestAccountRule.class);
            put(DefaultAccountRuleConf.class, DefaultAccountRule.class);
        }
    };

    private static final Map<Class<? extends PasswordRuleConf>, Class<? extends PasswordRule>> PASSWORD_RULE_CLASSES =
            new HashMap<Class<? extends PasswordRuleConf>, Class<? extends PasswordRule>>() {

        private static final long serialVersionUID = -6624291041977583649L;

        {
            put(TestPasswordRuleConf.class, TestPasswordRule.class);
            put(DefaultPasswordRuleConf.class, DefaultPasswordRule.class);
            put(HaveIBeenPwnedPasswordRuleConf.class, HaveIBeenPwnedPasswordRule.class);
        }
    };

    @Autowired
    private UserWorkflowAdapter uwf;

    @Autowired
    private AnySearchDAO anySearchDAO;

    @Autowired
    private DomainsHolder domainsHolder;

    @Autowired
    private TaskLogic taskLogic;

    @Override
    public Integer getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void load() {
        // in case Activiti is enabled, enable modifications for test users
        if (AopUtils.getTargetClass(uwf).getName().contains("Activiti")) {
            for (final Map.Entry<String, DataSource> entry : domainsHolder.getDomains().entrySet()) {
                AuthContextUtils.execWithAuthContext(entry.getKey(), new AuthContextUtils.Executable<Void>() {

                    @Override
                    public Void exec() {
                        JdbcTemplate jdbcTemplate = new JdbcTemplate(entry.getValue());
                        String procDef = jdbcTemplate.queryForObject(
                                "SELECT ID_ FROM ACT_RE_PROCDEF WHERE KEY_=?", String.class, "userWorkflow");

                        int counter = 0;
                        for (String user : jdbcTemplate.queryForList("SELECT id FROM SyncopeUser", String.class)) {
                            int value = counter++;
                            jdbcTemplate.update("INSERT INTO "
                                    + "ACT_RU_EXECUTION(ID_,REV_,PROC_INST_ID_,BUSINESS_KEY_,PROC_DEF_ID_,ACT_ID_,"
                                    + "IS_ACTIVE_,IS_CONCURRENT_,IS_SCOPE_,IS_EVENT_SCOPE_,SUSPENSION_STATE_) "
                                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                                    value, 2, value, "userWorkflow:" + user, procDef, "active",
                                    true, false, true, false, true);

                            value = counter++;
                            jdbcTemplate.update("INSERT INTO "
                                    + "ACT_RU_TASK(ID_,REV_,EXECUTION_ID_,PROC_INST_ID_,PROC_DEF_ID_,NAME_,"
                                    + "TASK_DEF_KEY_,PRIORITY_,CREATE_TIME_) "
                                    + "VALUES(?,?,?,?,?,?,?,?,?)",
                                    value, 2, value - 1, value - 1, procDef, "Active", "active", 50, new Date());

                            jdbcTemplate.update("UPDATE SyncopeUser SET workflowId=? WHERE id=?", value - 1, user);
                        }

                        return null;
                    }
                });
            }
        }

        // in case the Elasticsearch extension is enabled, reinit a clean index for all available domains
        if (AopUtils.getTargetClass(anySearchDAO).getName().contains("Elasticsearch")) {
            for (Map.Entry<String, DataSource> entry : domainsHolder.getDomains().entrySet()) {
                AuthContextUtils.execWithAuthContext(entry.getKey(), new AuthContextUtils.Executable<Void>() {

                    @Override
                    public Void exec() {
                        SchedTaskTO task = new SchedTaskTO();
                        task.setJobDelegateClassName(
                                "org.apache.syncope.core.provisioning.java.job.ElasticsearchReindex");
                        task.setName("Elasticsearch Reindex");
                        task = taskLogic.createSchedTask(TaskType.SCHEDULED, task);

                        taskLogic.execute(task.getKey(), null, false);

                        return null;
                    }
                });
            }
        }
    }

    @Override
    public Set<String> getClassNames(final Type type) {
        return CLASS_NAMES.get(type);
    }

    @Override
    public Set<Class<?>> getJWTSSOProviderClasses() {
        Set<Class<?>> classNames = new HashSet<>();
        classNames.add(SyncopeJWTSSOProvider.class);
        classNames.add(CustomJWTSSOProvider.class);
        return classNames;
    }

    @Override
    public Class<? extends Reportlet> getReportletClass(
            final Class<? extends ReportletConf> reportletConfClass) {

        return REPORTLET_CLASSES.get(reportletConfClass);
    }

    @Override
    public Class<? extends AccountRule> getAccountRuleClass(
            final Class<? extends AccountRuleConf> accountRuleConfClass) {

        return ACCOUNT_RULE_CLASSES.get(accountRuleConfClass);
    }

    @Override
    public Class<? extends PasswordRule> getPasswordRuleClass(
            final Class<? extends PasswordRuleConf> passwordRuleConfClass) {

        return PASSWORD_RULE_CLASSES.get(passwordRuleConfClass);
    }

    @Override
    public Set<Class<?>> getAuditAppenderClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(TestFileRewriteAuditAppender.class);
        classes.add(TestFileAuditAppender.class);
        return classes;
    }
}
