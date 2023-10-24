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
package org.talend.components.jdbc.output;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.jdbc.common.SchemaInfo;
import org.talend.components.jdbc.dataset.BaseDataSet;
import org.talend.components.jdbc.platforms.Platform;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.record.SchemaProperty;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

/**
 * SQL build tool for only runtime, for design time, we use another one : QueryUtils which consider the context.var and
 * so on
 *
 */
@Slf4j
public class JDBCSQLBuilder {

    private JDBCSQLBuilder() {
    }

    public static JDBCSQLBuilder getInstance() {
        return new JDBCSQLBuilder();
    }

    private static final String QUERY_TEMPLATE = "select %s from %s";

    public String generateSQL4SelectTable(Platform platform, String tableName, List<SchemaInfo> schema) {
        String columns = ofNullable(schema).filter(list -> !list.isEmpty())
                .map(l -> l.stream()
                        .map(column -> platform.identifier(column.getOriginalDbColumnName()))
                        .collect(Collectors.joining(",")))
                .orElse("*");
        // No need for the i18n service for this instance
        return String.format(QUERY_TEMPLATE, columns, platform.identifier(tableName));
    }

    public String generateSQL4DeleteTable(Platform platform, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ")
                .append(platform.delimiterToken())
                .append(tableName)
                .append(platform.delimiterToken());
        return sql.toString();
    }

    public String generateSQL4SnowflakeUpdate(Platform platform, String tableName, String tmpTableName,
            List<Column> columnList) {
        final List<String> updateKeys = new ArrayList<>();

        final List<String> updateValues = new ArrayList<>();

        final List<Column> all = getAllColumns(columnList);

        for (Column column : all) {
            if (column.updateKey) {
                updateKeys.add(column.dbColumnName);
            }

            if (column.updatable) {
                updateValues.add(column.dbColumnName);
            }
        }

        final StringBuilder sql = new StringBuilder();
        sql.append("merge into ")
                .append(tableName)
                .append(" target using ")
                .append(tmpTableName)
                .append(" as source on ");
        sql.append(updateKeys.stream()
                .map(name -> platform.identifier(name))
                .map(name -> "source." + name + "= target." + name)
                .collect(joining(" AND ")));
        sql.append(" when matched then update set ");
        sql.append(updateValues.stream()
                .map(name -> platform.identifier(name))
                .map(name -> "target." + name + "= source." + name)
                .collect(joining(",", "", " ")));

        return sql.toString();
    }

    public String generateSQL4SnowflakeUpsert(Platform platform, String tableName, String tmpTableName,
            List<Column> columnList) {
        final List<String> updateKeys = new ArrayList<>();

        final List<String> updateValues = new ArrayList<>();

        final List<String> insertValues = new ArrayList<>();

        final List<Column> all = getAllColumns(columnList);

        for (Column column : all) {
            if (column.updateKey) {
                updateKeys.add(column.dbColumnName);
            }

            if (column.updatable) {
                updateValues.add(column.dbColumnName);
            }

            if (column.insertable) {
                insertValues.add(column.dbColumnName);
            }
        }

        final StringBuilder sql = new StringBuilder();
        sql.append("merge into ")
                .append(tableName)
                .append(" target using ")
                .append(tmpTableName)
                .append(" as source on ");
        sql.append(updateKeys.stream()
                .map(name -> platform.identifier(name))
                .map(name -> "source." + name + "= target." + name)
                .collect(joining(" AND ")));
        sql.append(" when matched then update set ");
        sql.append(updateValues.stream()
                .map(name -> platform.identifier(name))
                .map(name -> "target." + name + "= source." + name)
                .collect(joining(",", "", " ")));
        sql.append(" when not matched then insert ");
        sql.append(insertValues.stream()
                .map(name -> platform.identifier(name))
                .map(name -> "target." + name)
                .collect(Collectors.joining(",", "(", ")")));
        sql.append(" values ");
        sql.append(insertValues.stream()
                .map(name -> platform.identifier(name))
                .map(name -> "source." + name)
                .collect(Collectors.joining(",", "(", ")")));

        return sql.toString();
    }

    public String generateSQL4SnowflakeDelete(Platform platform, String targetTable, String tmpTable,
            List<Column> columnList) {
        final List<String> deleteKeys = new ArrayList<>();

        final List<Column> all = getAllColumns(columnList);

        for (Column column : all) {
            if (column.deletionKey) {
                deleteKeys.add(column.dbColumnName);
            }
        }

        final StringBuilder sql = new StringBuilder();
        sql.append("delete from ")
                .append(targetTable)
                .append(" target using ")
                .append(tmpTable)
                .append(" as source where ");
        sql.append(deleteKeys.stream()
                .map(name -> platform.identifier(name))
                .map(name -> "source." + name + "= target." + name)
                .collect(joining(" AND ")));

        return sql.toString();
    }

    public static class Column {

        public String columnLabel;

        public String dbColumnName;

        public String sqlStmt = "?";

        public boolean isKey;

        public boolean updateKey;

        public boolean deletionKey;

        public boolean updatable = true;

        public boolean insertable = true;

        public boolean addCol;

        public List<Column> replacements;

        void replace(Column replacement) {
            if (replacements == null) {
                replacements = new ArrayList<Column>();
            }

            replacements.add(replacement);
        }

        public boolean isReplaced() {
            return this.replacements != null && !this.replacements.isEmpty();
        }

    }

    public String generateSQL4Insert(Platform platform, String tableName, List<Column> columnList) {
        List<String> dbColumnNames = new ArrayList<>();
        List<String> expressions = new ArrayList<>();

        List<Column> all = getAllColumns(columnList);

        for (Column column : all) {
            if (column.insertable) {
                dbColumnNames.add(column.dbColumnName);
                expressions.add(column.sqlStmt);
            }
        }

        return generateSQL4Insert(platform, tableName, dbColumnNames, expressions);
    }

    public List<Column> createColumnList(JDBCOutputConfig config, Schema schema) {
        return createColumnList(config, schema, true, null, null);
    }

    public List<Column> createColumnList(JDBCOutputConfig config, Schema schema, boolean isUseOriginColumnName,
            List<String> keys, List<String> ignoreColumns) {
        boolean missUpdateKey = true;
        boolean missDeleteKey = true;

        Map<String, Column> columnMap = new HashMap<>();
        List<Column> columnList = new ArrayList<>();

        final List<Schema.Entry> fields = schema.getEntries();

        keys = Optional.ofNullable(keys).orElse(new ArrayList<>());
        ignoreColumns = Optional.ofNullable(ignoreColumns).orElse(new ArrayList<>());

        for (Schema.Entry field : fields) {
            Column column = new Column();
            column.columnLabel = field.getName();
            // the javajet template have an issue for dynamic convert, it don't pass the origin column name
            String originName = field.getRawName();
            column.dbColumnName =
                    isUseOriginColumnName ? (originName != null ? originName : field.getName()) : field.getName();

            boolean isKey = Boolean.valueOf(field.getProp(SchemaProperty.IS_KEY)) || keys.contains(originName);
            if (isKey) {
                column.updateKey = true;
                column.deletionKey = true;
                column.updatable = false;

                missUpdateKey = false;
                missDeleteKey = false;
            } else {
                column.updateKey = false;
                column.deletionKey = false;

                if (ignoreColumns.contains(originName)) {
                    column.updatable = false;
                } else {
                    column.updatable = true;
                }
            }

            columnMap.put(field.getName(), column);
            columnList.add(column);
        }

        boolean enableFieldOptions = config.isUseFieldOptions();

        if (enableFieldOptions) {
            // TODO about dynamic support here:
            // for other javajet db output components, the dynamic support is static here, for example,
            // only can set all db columns in dynamic field all to update keys, can't use one db column in dynamic
            // column,
            // not sure which is expected, and now, here user can use one db column in dynamic column,
            // but user may not know the column label as it may be different with db column name as valid in java and
            // studio.
            // So here, we don't change anything, also as no much meaning for customer except making complex code
            List<FieldOption> fieldOptions = Optional.of(config.getFieldOptions()).orElse(Collections.emptyList());

            String dynamicColumnName = null;
            final BaseDataSet dataSet = config.getDataSet();
            if (dataSet != null) {
                final List<SchemaInfo> designFields = dataSet.getSchema();
                if (designFields != null) {
                    dynamicColumnName = designFields.stream()
                            .filter(field -> "id_Dynamic".equals(field.getTalendType()))
                            .map(field -> field.getLabel())
                            .findFirst()
                            .orElse(null);
                }
            }

            int i = 0;
            for (FieldOption fieldOption : fieldOptions) {
                String columnName = fieldOption.getColumnName();
                Column column = columnMap.get(columnName);
                if (column == null) {
                    if (columnName.equals(dynamicColumnName)) {
                        // skip for dynamic column as we not support in tcompv0 one too(even not show the column in ui
                        // in the field options table)
                        continue;
                    } else {
                        throw new RuntimeException(columnName + " column label doesn't exist for current target table");
                    }
                }
                column.updateKey = fieldOption.isUpdateKey();
                column.deletionKey = fieldOption.isDeleteKey();
                column.updatable = fieldOption.isUpdatable();
                column.insertable = fieldOption.isInsertable();

                if (column.updateKey) {
                    missUpdateKey = false;
                } else if (column.deletionKey) {
                    missDeleteKey = false;
                }

                i++;
            }
        }

        switch (config.getDataAction()) {
        case UPDATE:
        case INSERT_OR_UPDATE:
        case UPDATE_OR_INSERT: {
            if (missUpdateKey) {
                throw new RuntimeException("Miss key for update action");
            }
            break;
        }
        case DELETE: {
            if (missDeleteKey) {
                throw new RuntimeException("Miss key for delete action");
            }
            break;
        }
        default: {
            break;
        }
        }

        List<AdditionalColumn> additionalColumns = config.getAdditionalColumns();

        if (additionalColumns == null) {
            return columnList;
        }

        // here is a closed list in UI, even can't choose dynamic column, so no need to consider dynamic here
        int i = 0;
        for (AdditionalColumn additionalColumn : additionalColumns) {
            String referenceColumn = additionalColumn.getRefColumn();
            int j = 0;
            Column currentColumn = null;
            for (Column column : columnList) {
                if (column.columnLabel.equals(referenceColumn)) {
                    currentColumn = column;
                    break;
                }
                j++;
            }

            String newDBColumnName = additionalColumn.getColumnName();
            String sqlExpression = additionalColumn.getSqlExpression();

            Position position = additionalColumn.getPosition();
            if (position == Position.AFTER) {
                Column newColumn = new Column();
                newColumn.columnLabel = newDBColumnName;
                newColumn.dbColumnName = newDBColumnName;
                newColumn.sqlStmt = sqlExpression;
                newColumn.addCol = true;

                columnList.add(j + 1, newColumn);
            } else if (position == Position.BEFORE) {
                Column newColumn = new Column();
                newColumn.columnLabel = newDBColumnName;
                newColumn.dbColumnName = newDBColumnName;
                newColumn.sqlStmt = sqlExpression;
                newColumn.addCol = true;

                columnList.add(j, newColumn);
            } else if (position == Position.REPLACE) {
                Column replacementColumn = new Column();
                replacementColumn.columnLabel = newDBColumnName;
                replacementColumn.dbColumnName = newDBColumnName;
                replacementColumn.sqlStmt = sqlExpression;

                if (currentColumn != null) {
                    replacementColumn.isKey = currentColumn.isKey;
                    replacementColumn.updateKey = currentColumn.updateKey;
                    replacementColumn.deletionKey = currentColumn.deletionKey;
                    replacementColumn.insertable = currentColumn.insertable;
                    replacementColumn.updatable = currentColumn.updatable;

                    currentColumn.replace(replacementColumn);
                }
            }

            i++;
        }

        return columnList;
    }

    public String generateSQL4Insert(Platform platform, String tableName, Schema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ")
                .append(platform.delimiterToken())
                .append(tableName)
                .append(platform.delimiterToken())
                .append(" ");

        sb.append("(");

        List<Schema.Entry> fields = schema.getEntries();

        boolean firstOne = true;
        for (Schema.Entry field : fields) {
            if (firstOne) {
                firstOne = false;
            } else {
                sb.append(",");
            }

            String dbColumnName = field.getRawName();
            sb.append(platform.delimiterToken()).append(dbColumnName).append(platform.delimiterToken());
        }
        sb.append(")");

        sb.append(" VALUES ");

        sb.append("(");

        firstOne = true;
        for (@SuppressWarnings("unused")
        Schema.Entry field : fields) {
            if (firstOne) {
                firstOne = false;
            } else {
                sb.append(",");
            }

            sb.append("?");
        }
        sb.append(")");

        return sb.toString();
    }

    private String generateSQL4Insert(Platform platform, String tableName, List<String> insertableDBColumns,
            List<String> expressions) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ")
                .append(platform.delimiterToken())
                .append(tableName)
                .append(platform.delimiterToken())
                .append(" ");

        sb.append("(");
        boolean firstOne = true;
        for (String dbColumnName : insertableDBColumns) {
            if (firstOne) {
                firstOne = false;
            } else {
                sb.append(",");
            }

            sb.append(platform.delimiterToken()).append(dbColumnName).append(platform.delimiterToken());
        }
        sb.append(")");

        sb.append(" VALUES ");

        sb.append("(");

        firstOne = true;
        for (String expression : expressions) {
            if (firstOne) {
                firstOne = false;
            } else {
                sb.append(",");
            }

            sb.append(expression);
        }
        sb.append(")");

        return sb.toString();
    }

    public String generateSQL4Delete(Platform platform, String tableName, List<Column> columnList) {
        List<String> deleteKeys = new ArrayList<>();
        List<String> expressions = new ArrayList<>();

        List<Column> all = getAllColumns(columnList);

        for (Column column : all) {
            if (column.deletionKey) {
                deleteKeys.add(column.dbColumnName);
                expressions.add(column.sqlStmt);
            }
        }

        return generateSQL4Delete(platform, tableName, deleteKeys, expressions);
    }

    private String generateSQL4Delete(Platform platform, String tableName, List<String> deleteKeys,
            List<String> expressions) {
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ")
                .append(platform.delimiterToken())
                .append(tableName)
                .append(platform.delimiterToken())
                .append(" WHERE ");

        int i = 0;

        boolean firstOne = true;
        for (String dbColumnName : deleteKeys) {
            if (firstOne) {
                firstOne = false;
            } else {
                sb.append(" AND ");
            }

            sb.append(platform.delimiterToken())
                    .append(dbColumnName)
                    .append(platform.delimiterToken())
                    .append(" = ")
                    .append(expressions.get(i++));
        }

        return sb.toString();
    }

    private String generateSQL4Update(Platform platform, String tableName, List<String> updateValues,
            List<String> updateKeys,
            List<String> updateValueExpressions, List<String> updateKeyExpressions) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ")
                .append(platform.delimiterToken())
                .append(tableName)
                .append(platform.delimiterToken())
                .append(" SET ");

        int i = 0;

        boolean firstOne = true;
        for (String dbColumnName : updateValues) {
            if (firstOne) {
                firstOne = false;
            } else {
                sb.append(",");
            }

            sb.append(platform.delimiterToken())
                    .append(dbColumnName)
                    .append(platform.delimiterToken())
                    .append(" = ")
                    .append(updateValueExpressions.get(i++));
        }

        i = 0;

        sb.append(" WHERE ");

        firstOne = true;
        for (String dbColumnName : updateKeys) {
            if (firstOne) {
                firstOne = false;
            } else {
                sb.append(" AND ");
            }

            sb.append(platform.delimiterToken())
                    .append(dbColumnName)
                    .append(platform.delimiterToken())
                    .append(" = ")
                    .append(updateKeyExpressions.get(i++));
        }

        return sb.toString();
    }

    public String generateSQL4Update(Platform platform, String tableName, List<Column> columnList) {
        List<String> updateValues = new ArrayList<>();
        List<String> updateValueExpressions = new ArrayList<>();

        List<String> updateKeys = new ArrayList<>();
        List<String> updateKeyExpressions = new ArrayList<>();

        List<Column> all = getAllColumns(columnList);

        for (Column column : all) {
            if (column.updatable) {
                updateValues.add(column.dbColumnName);
                updateValueExpressions.add(column.sqlStmt);
            }

            if (column.updateKey) {
                updateKeys.add(column.dbColumnName);
                updateKeyExpressions.add(column.sqlStmt);
            }
        }

        return generateSQL4Update(platform, tableName, updateValues, updateKeys, updateValueExpressions,
                updateKeyExpressions);
    }

    private String generateQuerySQL4InsertOrUpdate(Platform platform, String tableName, List<String> updateKeys,
            List<String> updateKeyExpressions) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(1) FROM ")
                .append(platform.delimiterToken())
                .append(tableName)
                .append(platform.delimiterToken())
                .append(" WHERE ");

        int i = 0;

        boolean firstOne = true;
        for (String dbColumnName : updateKeys) {
            if (firstOne) {
                firstOne = false;
            } else {
                sb.append(" AND ");
            }

            sb.append(platform.delimiterToken())
                    .append(dbColumnName)
                    .append(platform.delimiterToken())
                    .append(" = ")
                    .append(updateKeyExpressions.get(i++));
        }

        return sb.toString();
    }

    public String generateQuerySQL4InsertOrUpdate(Platform platform, String tableName, List<Column> columnList) {
        List<String> updateKeys = new ArrayList<>();
        List<String> updateKeyExpressions = new ArrayList<>();

        List<Column> all = getAllColumns(columnList);

        for (Column column : all) {
            if (column.updateKey) {
                updateKeys.add(column.dbColumnName);
                updateKeyExpressions.add(column.sqlStmt);
            }
        }

        return generateQuerySQL4InsertOrUpdate(platform, tableName, updateKeys, updateKeyExpressions);
    }

    public static List<Column> getAllColumns(List<Column> columnList) {
        List<Column> result = new ArrayList<Column>();
        for (Column column : columnList) {
            if (column.replacements != null && !column.replacements.isEmpty()) {
                for (Column replacement : column.replacements) {
                    result.add(replacement);
                }
            } else {
                result.add(column);
            }
        }

        return result;
    }

}
