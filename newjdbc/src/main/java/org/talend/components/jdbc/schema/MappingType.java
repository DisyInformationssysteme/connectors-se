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
package org.talend.components.jdbc.schema;

import java.util.HashSet;
import java.util.Set;

/**
 * Type mapping between <code>SourceT</code> and <code>TargetT</code>
 * Provides target types to which source type can be mapped. Also provides default target mapping type
 */
public class MappingType<SourceT, TargetT> {

    private final SourceT sourceType;

    private final TargetT defaultTargetType;

    private final Set<TargetT> alternativeTargetTypes;

    public MappingType(SourceT sourceType, TargetT defaultTargetType, Set<TargetT> alternativeTargetTypes) {
        this.sourceType = sourceType;
        this.defaultTargetType = defaultTargetType;
        this.alternativeTargetTypes = new HashSet<>(alternativeTargetTypes);
    }

    public TargetT getDefaultType() {
        return defaultTargetType;
    }

    public Set<TargetT> getAdvisedTypes() {
        return alternativeTargetTypes;
    }

    public SourceT getSourceType() {
        return sourceType;
    }
}
