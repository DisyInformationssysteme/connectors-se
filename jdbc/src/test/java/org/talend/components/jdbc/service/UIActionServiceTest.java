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

    @Test void validateSQLInjection() {

        String[] correct = {"TableName","_my_identifier","My$identifier","идентификатор","内清表","3rd_identifier"};
        for (String name : correct) {
            final ValidationResult validationResult = uiActionService.validateSQLInjection(name);

            Assertions.assertTrue("the table name is valid".equals(validationResult.getComment()));
        }

        String[] riskyOnes = {"\"AAA\\\" ; drop table \\\"ABCDE\" ","105 OR 1=1" ,"45 --","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa259"};
        for (String name : riskyOnes) {
            final ValidationResult validationResult = uiActionService.validateSQLInjection(name);
            Assertions.assertFalse("the table name is valid".equals(validationResult.getComment()));
        }
    }
}