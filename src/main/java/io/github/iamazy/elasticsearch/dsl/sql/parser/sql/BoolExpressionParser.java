package io.github.iamazy.elasticsearch.dsl.sql.parser.sql;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import io.github.iamazy.elasticsearch.dsl.elastic.HighlightBuilders;
import io.github.iamazy.elasticsearch.dsl.sql.enums.SqlBoolOperator;
import io.github.iamazy.elasticsearch.dsl.sql.enums.SqlConditionType;
import io.github.iamazy.elasticsearch.dsl.sql.exception.ElasticSql2DslException;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.exact.BetweenAndQueryParser;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.exact.BinaryQueryParser;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.exact.InListQueryParser;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.method.MethodInvocation;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.method.fulltext.FullTextQueryParser;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.method.join.JoinQueryParser;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.method.score.ScoreQueryParser;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.method.script.ScriptQueryParser;
import io.github.iamazy.elasticsearch.dsl.sql.parser.query.method.term.TermLevelAtomicQueryParser;
import io.github.iamazy.elasticsearch.dsl.sql.model.AtomicQuery;
import io.github.iamazy.elasticsearch.dsl.sql.model.SqlCondition;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BoolExpressionParser {

    private final TermLevelAtomicQueryParser termLevelAtomicQueryParser;
    private final ScriptQueryParser scriptQueryParser;
    private final FullTextQueryParser fullTextAtomQueryParser;
    private final BinaryQueryParser binaryQueryParser;
    private final InListQueryParser inListQueryParser;
    private final BetweenAndQueryParser betweenAndQueryParser;

    private final JoinQueryParser joinAtomQueryParser;
    private final ScoreQueryParser scoreQueryParser;

    @Getter
    private Set<String> highlighter;

    public BoolExpressionParser() {
        highlighter=new HashSet<>(0);
        termLevelAtomicQueryParser = new TermLevelAtomicQueryParser();
        fullTextAtomQueryParser = new FullTextQueryParser();
        binaryQueryParser = new BinaryQueryParser();
        inListQueryParser = new InListQueryParser();
        betweenAndQueryParser = new BetweenAndQueryParser();
        scriptQueryParser = new ScriptQueryParser();
        joinAtomQueryParser = new JoinQueryParser();
        scoreQueryParser=new ScoreQueryParser();
    }


    public BoolQueryBuilder parseBoolQueryExpr(SQLExpr conditionExpr, String queryAs) {
        SqlCondition sqlCondition = recursiveParseBoolQueryExpr(conditionExpr, queryAs);
        SqlBoolOperator operator = sqlCondition.getOperator();

        if (SqlConditionType.Atom == sqlCondition.getConditionType()) {
            operator = SqlBoolOperator.AND;
        }
        return mergeAtomicQuery(sqlCondition.getQueryList(), operator);
    }

    private SqlCondition recursiveParseBoolQueryExpr(SQLExpr conditionExpr, String queryAs) {
        if (conditionExpr instanceof SQLBinaryOpExpr) {
            SQLBinaryOpExpr binOpExpr = (SQLBinaryOpExpr) conditionExpr;
            SQLBinaryOperator binOperator = binOpExpr.getOperator();

            if (SQLBinaryOperator.BooleanAnd == binOperator || SQLBinaryOperator.BooleanOr == binOperator) {
                SqlBoolOperator operator = SQLBinaryOperator.BooleanAnd == binOperator ? SqlBoolOperator.AND : SqlBoolOperator.OR;

                SqlCondition leftCondition = recursiveParseBoolQueryExpr(binOpExpr.getLeft(), queryAs);
                SqlCondition rightCondition = recursiveParseBoolQueryExpr(binOpExpr.getRight(), queryAs);

                List<AtomicQuery> mergedQueryList = Lists.newArrayList();
                combineQueryBuilder(mergedQueryList, leftCondition, operator);
                combineQueryBuilder(mergedQueryList, rightCondition, operator);

                return new SqlCondition(mergedQueryList, operator);
            }
        }
        else if (conditionExpr instanceof SQLNotExpr) {
            SqlCondition innerSQLCondition = recursiveParseBoolQueryExpr(((SQLNotExpr) conditionExpr).getExpr(), queryAs);

            SqlBoolOperator operator = innerSQLCondition.getOperator();
            if (SqlConditionType.Atom == innerSQLCondition.getConditionType()) {
                operator = SqlBoolOperator.AND;
            }

            BoolQueryBuilder boolQuery = mergeAtomicQuery(innerSQLCondition.getQueryList(), operator);
            boolQuery = QueryBuilders.boolQuery().mustNot(boolQuery);

            return new SqlCondition(new AtomicQuery(boolQuery), SqlConditionType.Atom);
        }
        return new SqlCondition(parseAtomQueryCondition(conditionExpr, queryAs), SqlConditionType.Atom);
    }

    private AtomicQuery parseAtomQueryCondition(SQLExpr sqlConditionExpr, String queryAs) {
        if (sqlConditionExpr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr methodQueryExpr = (SQLMethodInvokeExpr) sqlConditionExpr;

            MethodInvocation methodInvocation = new MethodInvocation(methodQueryExpr, queryAs);

            if (scriptQueryParser.isMatchMethodInvocation(methodInvocation)) {
                return scriptQueryParser.parseMethodQuery(methodInvocation);
            }

            if (fullTextAtomQueryParser.isFulltextAtomQuery(methodInvocation)) {
                return fullTextAtomQueryParser.parseFullTextAtomQuery(methodQueryExpr, queryAs);
            }

            if (termLevelAtomicQueryParser.isTermLevelAtomQuery(methodInvocation)) {
                return termLevelAtomicQueryParser.parseTermLevelAtomQuery(methodQueryExpr, queryAs);
            }

            if (joinAtomQueryParser.isJoinAtomQuery(methodInvocation)) {
                return joinAtomQueryParser.parseJoinAtomQuery(methodQueryExpr, queryAs);
            }

            if(scoreQueryParser.isScoreAtomQuery(methodInvocation)){
                return scoreQueryParser.parseScoreAtomQuery(methodQueryExpr,queryAs);
            }
        }
        else if (sqlConditionExpr instanceof SQLBinaryOpExpr) {
            return binaryQueryParser.parseBinaryQuery((SQLBinaryOpExpr) sqlConditionExpr, queryAs);
        }
        else if (sqlConditionExpr instanceof SQLInListExpr) {
            return inListQueryParser.parseInListQuery((SQLInListExpr) sqlConditionExpr, queryAs);
        }
        else if (sqlConditionExpr instanceof SQLBetweenExpr) {
            return betweenAndQueryParser.parseBetweenAndQuery((SQLBetweenExpr) sqlConditionExpr, queryAs);
        }

        throw new ElasticSql2DslException(String.format("[syntax error] Can not support query condition type[%s]", sqlConditionExpr.toString()));
    }

    private void combineQueryBuilder(List<AtomicQuery> combiner, SqlCondition sqlCondition, SqlBoolOperator binOperator) {
        if (SqlConditionType.Atom == sqlCondition.getConditionType() || sqlCondition.getOperator() == binOperator) {
            combiner.addAll(sqlCondition.getQueryList());
        }
        else {
            BoolQueryBuilder boolQuery = mergeAtomicQuery(sqlCondition.getQueryList(), sqlCondition.getOperator());
            combiner.add(new AtomicQuery(boolQuery));
        }
    }

    private BoolQueryBuilder mergeAtomicQuery(List<AtomicQuery> atomQueryList, SqlBoolOperator operator) {
        BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
        ListMultimap<AtomicQuery, QueryBuilder> listMultiMap = ArrayListMultimap.create();

        for (AtomicQuery atomQuery : atomQueryList) {
            if(CollectionUtils.isNotEmpty(atomQuery.getHighlighter())&&!atomQuery.isNestedQuery()){
                highlighter.addAll(atomQuery.getHighlighter());
            }
            if (Boolean.FALSE == atomQuery.isNestedQuery()) {
                if (operator == SqlBoolOperator.AND) {
                    subBoolQuery.must(atomQuery.getQueryBuilder());
                }
                if (operator == SqlBoolOperator.OR) {
                    subBoolQuery.should(atomQuery.getQueryBuilder());
                }
            }
            else {
                listMultiMap.put(atomQuery, atomQuery.getQueryBuilder());
            }
        }

        for (AtomicQuery atomicQuery : listMultiMap.keySet()) {
            List<QueryBuilder> nestedQueryList = listMultiMap.get(atomicQuery);
            ArrayList<String> nestedDocPrefix=atomicQuery.getNestedQueryPath();
            if (nestedQueryList.size() == 1) {
                if (operator == SqlBoolOperator.AND) {
                    if(nestedDocPrefix.size()==1) {
                        if(CollectionUtils.isNotEmpty(atomicQuery.getHighlighter())){
                            HighlightBuilder highlightBuilder= HighlightBuilders.highlighter(atomicQuery.getHighlighter());
                            InnerHitBuilder innerHitBuilder=new InnerHitBuilder(atomicQuery.getQueryBuilder().getName()+"_highlighter").setHighlightBuilder(highlightBuilder);
                            subBoolQuery.must(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), nestedQueryList.get(0), ScoreMode.Avg).innerHit(innerHitBuilder));
                        }else {
                            subBoolQuery.must(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), nestedQueryList.get(0), ScoreMode.Avg));
                        }
                    }else if(nestedDocPrefix.size()==2){
                        if(CollectionUtils.isNotEmpty(atomicQuery.getHighlighter())){
                            HighlightBuilder highlightBuilder= HighlightBuilders.highlighter(atomicQuery.getHighlighter());
                            InnerHitBuilder innerHitBuilder=new InnerHitBuilder(atomicQuery.getQueryBuilder().getName()+"_highlighter").setHighlightBuilder(highlightBuilder);
                            subBoolQuery.must(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), QueryBuilders.nestedQuery(nestedDocPrefix.get(1), nestedQueryList.get(0), ScoreMode.Avg).innerHit(innerHitBuilder), ScoreMode.Avg));
                        }else {
                            subBoolQuery.must(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), QueryBuilders.nestedQuery(nestedDocPrefix.get(1), nestedQueryList.get(0), ScoreMode.Avg), ScoreMode.Avg));
                        }
                    }
                }
                if (operator == SqlBoolOperator.OR) {
                    if(nestedDocPrefix.size()==1) {
                        if(CollectionUtils.isNotEmpty(atomicQuery.getHighlighter())){
                            HighlightBuilder highlightBuilder= HighlightBuilders.highlighter(atomicQuery.getHighlighter());
                            InnerHitBuilder innerHitBuilder=new InnerHitBuilder(atomicQuery.getQueryBuilder().getName()+"_highlighter").setHighlightBuilder(highlightBuilder);
                            subBoolQuery.should(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), nestedQueryList.get(0), ScoreMode.Avg).innerHit(innerHitBuilder));
                        }else {
                            subBoolQuery.should(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), nestedQueryList.get(0), ScoreMode.Avg));
                        }
                    }else if(nestedDocPrefix.size()==2){
                        if(CollectionUtils.isNotEmpty(atomicQuery.getHighlighter())){
                            HighlightBuilder highlightBuilder= HighlightBuilders.highlighter(atomicQuery.getHighlighter());
                            InnerHitBuilder innerHitBuilder=new InnerHitBuilder(atomicQuery.getQueryBuilder().queryName()+"_highlighter").setHighlightBuilder(highlightBuilder);
                            subBoolQuery.should(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), QueryBuilders.nestedQuery(nestedDocPrefix.get(1), nestedQueryList.get(0), ScoreMode.Avg).innerHit(innerHitBuilder), ScoreMode.Avg));
                        }else {
                            subBoolQuery.should(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), QueryBuilders.nestedQuery(nestedDocPrefix.get(1), nestedQueryList.get(0), ScoreMode.Avg), ScoreMode.Avg));
                        }
                    }
                }
                continue;
            }

            BoolQueryBuilder boolNestedQuery = QueryBuilders.boolQuery();
            for (QueryBuilder nestedQueryItem : nestedQueryList) {
                if (operator == SqlBoolOperator.AND) {
                    boolNestedQuery.must(nestedQueryItem);
                }
                if (operator == SqlBoolOperator.OR) {
                    boolNestedQuery.should(nestedQueryItem);
                }
            }

            if (operator == SqlBoolOperator.AND) {
                if(nestedDocPrefix.size()==1) {
                    subBoolQuery.must(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), boolNestedQuery, ScoreMode.Avg));
                }else if(nestedDocPrefix.size()==2){
                    subBoolQuery.must(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), QueryBuilders.nestedQuery(nestedDocPrefix.get(1),boolNestedQuery,ScoreMode.Avg), ScoreMode.Avg));
                }
            }
            if (operator == SqlBoolOperator.OR) {
                if(nestedDocPrefix.size()==1) {
                    subBoolQuery.should(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), boolNestedQuery, ScoreMode.Avg));
                }else if(nestedDocPrefix.size()==2){
                    subBoolQuery.should(QueryBuilders.nestedQuery(nestedDocPrefix.get(0), QueryBuilders.nestedQuery(nestedDocPrefix.get(1),boolNestedQuery,ScoreMode.Avg), ScoreMode.Avg));
                }
            }

        }
        return subBoolQuery;
    }


}
