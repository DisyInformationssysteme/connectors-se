/*
 * Copyright (C) 2006-2023 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.talend.components.jdbc.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.asyncvalidation.ValidationResult;
import org.talend.sdk.component.junit5.WithComponents;

@WithComponents("org.talend.components.jdbc")
class UIActionServiceTest {

    @Service
    private UIActionService uiActionService;

    @Test
    void validateSQLInjection() {

        String[] correct = { "TableName", "_my_identifier", "My$identifier", "идентификатор", "内清表", "3rd_identifier" };
        for (String name : correct) {
            final ValidationResult validationResult = uiActionService.validateSQLInjection(name);

            Assertions.assertTrue("the table name is valid".equals(validationResult.getComment()));
        }

        String[] riskyOnes = { "\"AAA\\\" ; drop table \\\"ABCDE\" ", "105 OR 1=1", "45 --",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa259" };
        for (String name : riskyOnes) {
            final ValidationResult validationResult = uiActionService.validateSQLInjection(name);
            Assertions.assertFalse("the table name is valid".equals(validationResult.getComment()));
        }
    }
}