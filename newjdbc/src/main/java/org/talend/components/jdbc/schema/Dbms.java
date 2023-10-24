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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides information of type mapping for Database Management System (DBMS)
 * This data is retrieved from external configuration for JDBC components
 */
public class Dbms {

    private final String id;

    private final String product;

    private final String label;

    private final boolean isDefaultDbms;

    private final Map<String, DbmsType> types = new HashMap<>();

    private final Map<String, MappingType<TalendType, DbmsType>> talendToDbmsMapping = new HashMap<>();

    private final Map<String, MappingType<DbmsType, TalendType>> dbmsToTalendMapping = new HashMap<>();

    /**
     * Sets id, product, label and whether DBMS is default
     * 
     * @param id
     * @param product
     * @param label
     * @param isDefault
     */
    public Dbms(String id, String product, String label, boolean isDefault) {
        this.id = id;
        this.product = product;
        this.label = label;
        this.isDefaultDbms = isDefault;
    }

    /**
     * Getter for dbmsTypes.
     * 
     * @return the dbmsTypes
     */
    public List<String> getDbmsTypes() {
        return new ArrayList<String>(types.keySet());
    }

    /**
     * Getter for id.
     * 
     * @return the id
     */
    public String getId() {
        return this.id;
    }

    /**
     * Getter for label.
     * 
     * @return the label
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * Getter for product.
     * 
     * @return the product
     */
    public String getProduct() {
        return this.product;
    }

    /**
     * Returns DBMS type by its name
     * 
     * @param dbmsTypeName DBMS type name
     * @return DBMS type
     */
    public DbmsType getDbmsType(String dbmsTypeName) {
        return types.get(dbmsTypeName);
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("Dbms["); //$NON-NLS-1$
        buffer.append("product = ").append(product); //$NON-NLS-1$
        buffer.append(", id = ").append(id); //$NON-NLS-1$
        buffer.append(", label = ").append(label); //$NON-NLS-1$
        buffer.append(", dbmsTypes = ").append(getDbmsTypes()); //$NON-NLS-1$
        buffer.append("]"); //$NON-NLS-1$
        return buffer.toString();
    }

    /**
     * Adds DBMS type. It is used by configuration parser to fill this Dbms object
     * 
     * @param typeName DBMS type name
     * @param type DBMS type
     */
    void addType(String typeName, DbmsType type) {
        types.put(typeName, type);
    }

    /**
     * Returns mapping for Talend type as source type
     * 
     * @param talendTypeName Talend type name
     * @return Mapping object describing all DBMS types to which this Talend type may be mapped
     */
    public MappingType<TalendType, DbmsType> getTalendMapping(String talendTypeName) {
        return talendToDbmsMapping.get(talendTypeName);
    }

    /**
     * Returns mapping for DBMS type as source type
     * 
     * @param dbmsTypeName DBMS type name
     * @return Mapping object describing all Talend types to which this DBMS type may be mapped
     */
    public MappingType<DbmsType, TalendType> getDbmsMapping(String dbmsTypeName) {
        return dbmsToTalendMapping.get(dbmsTypeName);
    }

    /**
     * Returns set of advised Talend types for specified DBMS type
     * 
     * @param dbmsTypeName DBMS type name
     * @return advised Talend types
     */
    public Set<TalendType> getAdvisedTalendTypes(String dbmsTypeName) {
        return getDbmsMapping(dbmsTypeName).getAdvisedTypes();
    }

    /**
     * Returns set of advised DBMS types for specified Talend type
     * 
     * @param talendTypeName Talend type name
     * @return advised DBMS types
     */
    public Set<DbmsType> getAdvisedDbmsTypes(String talendTypeName) {
        return getTalendMapping(talendTypeName).getAdvisedTypes();
    }

    /**
     * Checks whether specified Talend type is advised mapping for specified DBMS Talend type
     * 
     * @param talendTypeName Talend type name
     * @param dbmsTypeName DBMS type name
     * @return true, if it is advised; false otherwise
     */
    public boolean isAdvisedTalendType(String talendTypeName, String dbmsTypeName) {
        TalendType talendType = TalendType.get(talendTypeName);
        return getAdvisedTalendTypes(dbmsTypeName).contains(talendType);
    }

    /**
     * Checks whether specified DBMS type is advised mapping for specified Talend type
     * 
     * @param dbmsTypeName DBMS type name
     * @param talendTypeName Talend type name
     * @return true, if it is advised; false otherwise
     */
    public boolean isAdvisedDbmsType(String dbmsTypeName, String talendTypeName) {
        DbmsType dbmsType = getDbmsType(dbmsTypeName);
        return getAdvisedDbmsTypes(talendTypeName).contains(dbmsType);
    }

    void addTalendMapping(String sourceTypeName, MappingType<TalendType, DbmsType> mapping) {
        talendToDbmsMapping.put(sourceTypeName, mapping);
    }

    void addDbMapping(String sourceTypeName, MappingType<DbmsType, TalendType> mapping) {
        dbmsToTalendMapping.put(sourceTypeName, mapping);
    }

    /**
     * Getter for defaultDbms.
     * 
     * @return the defaultDbms
     */
    boolean isDefaultDbms() {
        return isDefaultDbms;
    }

}
