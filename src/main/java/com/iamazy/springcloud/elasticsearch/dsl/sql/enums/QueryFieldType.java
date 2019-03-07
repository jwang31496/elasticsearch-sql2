package com.iamazy.springcloud.elasticsearch.dsl.sql.enums;

/**
 * @author iamazy
 * @date 2019/2/19
 * @descrition
 **/
public enum  QueryFieldType {
    MatchAllField,
    SqlSelectField,
    RootDocField,
    InnerDocField,
    NestedDocField
}