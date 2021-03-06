package io.github.iamazy.elasticsearch.dsl.sql.druid;

import com.alibaba.druid.sql.parser.Keywords;
import com.alibaba.druid.sql.parser.Lexer;
import com.alibaba.druid.sql.parser.Token;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * @author iamazy
 * @date 2019/2/19
 **/
class ElasticSqlLexer extends Lexer {
    private final static Keywords DEFAULT_ELASTIC_SQL_KEYWORDS;

    static {
        Map<String, Token> map= Maps.newHashMap();
        map.put("SELECT", Token.SELECT);
        map.put("DISTINCT", Token.DISTINCT);
        map.put("FROM", Token.FROM);
        map.put("WHERE", Token.WHERE);

        map.put("GROUP", Token.GROUP);
        map.put("HAVING", Token.HAVING);

        map.put("AND", Token.AND);
        map.put("AS", Token.AS);

        map.put("ORDER", Token.ORDER);
        map.put("BY", Token.BY);

        map.put("ASC", Token.ASC);
        map.put("DESC", Token.DESC);

        map.put("LIMIT", Token.LIMIT);
        map.put("IS", Token.IS);
        map.put("BETWEEN", Token.BETWEEN);

        map.put("IN", Token.IN);
        map.put("EXISTS", Token.EXISTS);

        map.put("LIKE", Token.LIKE);
        map.put("NOT", Token.NOT);

        map.put("NULL", Token.NULL);
        map.put("OR", Token.OR);

        map.put("XOR", Token.XOR);
        map.put("COMMENT", Token.COMMENT);

        map.put("QUERY", Token.INDEX);
        map.put("ROUTING", Token.INDEX);
        map.put("NESTED", Token.INDEX);
        map.put("INNER", Token.INNER);
        map.put("JOIN", Token.JOIN);

        map.put("SCROLL",Token.CURSOR);



        //delete
        map.put("TOP",Token.TOP);
        map.put("DELETE",Token.DELETE);
        map.put("UPDATE",Token.UPDATE);
        map.put("SET",Token.SET);

        DEFAULT_ELASTIC_SQL_KEYWORDS=new Keywords(map);
    }

    private ElasticSqlLexer(String input,boolean skipComment){
        super(input,skipComment);
        super.keywods=DEFAULT_ELASTIC_SQL_KEYWORDS;
    }

    ElasticSqlLexer(String input){
        this(input,false);
    }
}




















































