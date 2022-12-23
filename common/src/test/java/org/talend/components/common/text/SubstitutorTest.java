/*
 * Copyright (C) 2006-2022 Talend Inc. - www.talend.com
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
package org.talend.components.common.text;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SubstitutorTest {

    @ParameterizedTest
    @CsvSource(value = {
            "${,},'',No placeholder in the sentence.,No placeholder in the sentence.",
            "${,},'',This is a simple ${place_holder}.,This is a simple P_L_A_C_E_H_O_L_D_E_R.",
            "((,)),'',A more ((complex)) example with ((two)) place holders.,A more C_O_M_P_L_E_X example with T_W_O place holders.",
            "${,},'',Example ${three} with \\${escape} ${place_holder}.,Example 3 with ${escape} P_L_A_C_E_H_O_L_D_E_R.",
            "[,],'',[one] key with [unknown:-MY_DEFAULT] value,O_N_E key with MY_DEFAULT value",
            "${,},'',${one} ${two} ${three},O_N_E T_W_O 3",
            "${,},'',${aaa:-AAA} ${bbb:-BBB} ${ccc:-CCC},AAA BBB CCC",
            "[START[,]STOP],'',[START[START]STOP] [START[STOP]STOP],begin end",
            "[[,]],'',[[START]] [[STOP]],begin end",
            "[[,]],'',example without substitution,example without substitution",
            "{,},'',This is dssl {.record.user{age > 40}},This is dssl a_user",
            "${,}$,'',This is dssl ${.record.user{age > 40}}$,This is dssl a_user",
            "${,}$,'',This is dssl ${.record.user${age > 40}$}$,This is dssl another_user",
            "${,}$,'',This is dssl ${.record.user${age > 40}$}$ end.,This is dssl another_user end.",
            "${,}$,.input,This is dssl ${.input.record.user${age > 40}$}$ end.,This is dssl another_user end.",
            "{,},.input,This is dssl {.input.record.user{age > 40}} end.,This is dssl a_user end.",
            "{,},.input,This is dssl {.input.record.xuserx{age > 40}:-no_user} end.,This is dssl no_user end.",
            "${,}$,'',a,a",
            "${,}$,'',,null",
            "{,},.response,This is an input {.input.aaa.bbb} and a response {.response.aaa.bbb}.,This is an input {.input.aaa.bbb} and a response ok."
    })
    void testSubstitutor(final String prefix, final String suffix, final String keyPrefix, final String value,
            final String expected) {
        final Map<String, String> store = new HashMap<>();
        store.put("place_holder", "P_L_A_C_E_H_O_L_D_E_R");
        store.put("complex", "C_O_M_P_L_E_X");
        store.put("one", "O_N_E");
        store.put("two", "T_W_O");
        store.put("escape", "E_S_C_A_P_E");
        store.put("three", "3");
        store.put("START", "begin");
        store.put("STOP", "end");
        store.put(".record.user{age > 40}", "a_user");
        store.put(".record.user${age > 40}$", "another_user");
        store.put(".aaa.bbb", "ok");

        Substitutor.KeyFinder kf = new Substitutor.KeyFinder(prefix, suffix, keyPrefix);
        final Substitutor substitutor = new Substitutor(kf, store::get);

        final String transformed = substitutor.replace(value);

        if ("null".equals(expected)) {
            Assertions.assertNull(transformed);
        } else {
            Assertions.assertEquals(expected, transformed);
        }

    }

}