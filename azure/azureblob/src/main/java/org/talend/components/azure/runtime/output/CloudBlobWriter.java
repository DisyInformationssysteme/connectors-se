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
package org.talend.components.azure.runtime.output;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageException;

public interface CloudBlobWriter {

    void upload(final byte[] buffer) throws IOException, StorageException;

    void append(final byte[] buffer, OperationContext opContext) throws IOException, StorageException;

    void upload(InputStream sourceStream) throws StorageException, IOException;

    void appendText(final String content) throws StorageException, IOException;

    @FunctionalInterface
    interface OutputFunction {

        void onOutputStream(OutputStream out) throws IOException;
    }

    void onOutput(OutputFunction action) throws IOException, StorageException;

}
