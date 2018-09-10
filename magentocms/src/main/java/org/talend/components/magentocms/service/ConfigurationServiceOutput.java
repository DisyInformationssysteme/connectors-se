package org.talend.components.magentocms.service;

import lombok.Getter;
import lombok.Setter;
import org.talend.components.magentocms.output.MagentoCmsOutputConfiguration;
import org.talend.sdk.component.api.service.Service;

@Service
@Getter
@Setter
public class ConfigurationServiceOutput {

    private MagentoCmsOutputConfiguration magentoCmsOutputConfiguration;

}