package cn.suniper.cenj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Rao Mengnan
 *         on 2018/8/24.
 */
public class Interpreter {
    private String originName;
    private List<String> words;
    private Verb verb;
    private String tableName;
    private String sql;

    private static final List<Predicate> separatorList = Arrays.asList(
            Predicate.By,
            Predicate.And,
            Predicate.Or,
            Predicate.OrderBy
    );

    public Interpreter(String methodName, String tableName) {
        this.originName = methodName;
        this.words = StringUtil.splitCamelWords(originName);
        this.tableName = tableName;

        String verbStr = words.remove(0);
        this.verb = Verb.getVerb(verbStr);
        if (this.verb == null) {
            throw new IllegalArgumentException(String.format("Illegal method name: wrong verb: %s", verbStr));
        }

        this.sql = buildSql();
    }

    private String buildSql() {
        StringBuilder sql = new StringBuilder(buildQuery());

        // > 0 ; < 0; == 0;
        int indexOfWhere = sql.indexOf("WHERE");
        if (indexOfWhere < 0) {

            String target = "*";
            if (sql.length() > 0) {
                String first = sql.indexOf(" ") > 0 ? sql.substring(0, sql.indexOf(" ")) : sql.toString();
                if (Predicate.contains(first)) {
                    // normal predicate
                    target = "*";
                } else if (first.equals("TOP")) {
                    // findTop10[OrderByXXX...]()
                    int next = sql.indexOf(" ", 4);
                    next = next == -1 ? sql.length() : next;
                    target = sql.substring(0, next);
                    sql.replace(0, next + 1, "");
                } else {
                    // findName[OrderByXXX...]()
                    target = first;
                    sql.replace(0, first.length() + 1, "");
                }
            }

            buildSql(sql, indexOfWhere, target);

        } else if (indexOfWhere == 0) {
            String target = "*";
            buildSql(sql, indexOfWhere, target);
        } else {
            buildSql(sql, indexOfWhere, null);
        }

        return sql.toString().toUpperCase().trim();

    }

    private void buildSql(StringBuilder sql, int indexOfWhere, String target) {
        String left = verb == Verb.Count ? "(" : "";
        String right = verb == Verb.Count ? ")" : "";
        String verbName = verb.getSymbol() + (verb == Verb.Count ? "" : " ");

        if (indexOfWhere <= 0) {
            StringBuilder header = new StringBuilder(verbName);
            header.append(left)
                    .append(target)
                    .append(right)
                    .append(" FROM ")
                    .append(tableName)
                    .append(" ");
            sql.insert(0, header);
        } else {
            sql.insert(indexOfWhere, right + " FROM " + tableName + " ");
            sql.insert(0, verbName + left);
        }
    }

    private String buildQuery() {

        List<Integer> separatorIndexList = new ArrayList<>();
        List<List<String>> subList = new ArrayList<>();
        int last = -1;
        for (int index = 0; index < words.size(); index++) {
            if (!separatorList.contains(Predicate.get(words.get(index)))) {
                continue;
            }

            if (index > 0 && "Order".equals(words.get(index - 1))) {
                words.set(index, "OrderBy");
                words.set(index - 1, "");
            }

            separatorIndexList.add(index);
            if (index == 0) {
                last = index;
                continue;
            }
            List<String> sub = words.subList(last + 1, index);
            subList.add(sub);
            last = index;
        }

        // attach
        if (last < words.size()) {
            subList.add(words.subList(last + 1, words.size()));
        }

        String query = assembleSegments(separatorIndexList, subList).trim();
        // findAll() equivalent to find()
        return query.equals("ALL") ? "" : query;
    }

    private String assembleSegments(List<Integer> separatorIndex, List<List<String>> partsList) {
        // (first) (top n) (by xxx) (and xxx) (or xxx)
        StringBuilder query = new StringBuilder();
        if (partsList.size() > separatorIndex.size()) {
            // first | top n
            // <First> | <TopN> [By xxx and xxx or xxx]
            assert separatorIndex.size() == partsList.size() - 1;
            query.append(String.join(" ", partsList.remove(0)));
            if (query.toString().startsWith("Top")) {
                query.insert(3, " ");
            }
        } else if (partsList.size() < separatorIndex.size()) {
            throw new IllegalArgumentException("Illegal method name format");
        }

        for (int i = 0; i < partsList.size(); i++) {
            // findByAgeOrderByLastNameDesc
            String separatorName = words.get(separatorIndex.get(i));
            Predicate separator = Predicate.get(separatorName);
            List<String> parts = partsList.get(i);
            query.append(separator.symbol)
                    .append(" ")
                    .append(assembleWords2Segment(parts, separator))
                    .append(" ");
        }

        return query.toString().toUpperCase();
    }

    private String assembleWords2Segment(List<String> parts, Predicate seaparator) {
        StringBuilder segment = new StringBuilder();
        String predicateTmp = null;
        boolean include = false;
        int index = 0;
        for (String part : parts) {

            if (predicateTmp == null && Predicate.isPrefix(part)) {
                predicateTmp = part;
                continue;
            }

            if (predicateTmp != null) {
                if (Predicate.get(predicateTmp + part) != null) {
                    predicateTmp += part;
                    continue;
                } else {
                    segment.append(" ").append(Predicate.get(predicateTmp).symbol).append(" ");
                    include = true;
                    predicateTmp = null;
                }
            }
            segment.append(part);
            if (index >= parts.size()) break;
        }

        // attach
        if (predicateTmp != null) {
            segment.append(" ").append(Predicate.get(predicateTmp).symbol);
            include = true;
        }

        if (!include && seaparator != Predicate.OrderBy) {
            segment.append(" ").append(Predicate.Equals.symbol);
        }

        return segment.toString();
    }

    public String getOriginName() {
        return originName;
    }

    public String getSQL() {
        return this.sql;
    }

    @Override
    public String toString() {
        return "Origin: " + originName + "\n"
                + "SQL: " + sql;
    }
}