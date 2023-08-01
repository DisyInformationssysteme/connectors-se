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
package org.talend.components.pgp.encrypt;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

import org.pgpainless.algorithm.DocumentSignatureType;
import org.pgpainless.algorithm.SignatureType;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Proposable;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

@Data
@NoArgsConstructor
@GridLayout({
        @GridLayout.Row({ "action" })
})
@GridLayout(names = GridLayout.FormType.ADVANCED, value = {
        @GridLayout.Row({ "signatureType" }),
        @GridLayout.Row({ "armored" }),
        @GridLayout.Row({ "comment" })
})
@Documentation("Configuration for the Encrypt.")
public class PGPEncryptConfig implements Serializable {

    @Option
    @ActiveIf(target = "action", value = { "EncryptAndSign", "Sign" })
    @Documentation("See org.pgpainless.algorithm.SignatureType for details.")
    private SignatureType signatureType = SignatureType.BINARY_DOCUMENT;

    public enum SignatureType {
        BINARY_DOCUMENT,
        CANONICAL_TEXT_DOCUMENT
    }

    @Option
    @Documentation("Use Ascii Armoring on the output.")
    private boolean armored = true;

    @Option
    @Documentation("Comment to be added for the output message.")
    @ActiveIf(target = "armored", value = "true")
    private String comment = "Message encrypted by Talend";

    public enum BehaviorEnum {
        Encrypt,
        EncryptAndSign,
        Sign
    }

    @Option
    @Documentation("Encrypt action")
    private BehaviorEnum action = BehaviorEnum.Encrypt;
}