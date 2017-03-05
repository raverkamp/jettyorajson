/*

 Copyright (c) 2015, Roland Averkamp, roland.averkamp.0@gmail.com

 Permission to use, copy, modify, and/or distribute this software for any
 purpose with or without fee is hereby granted, provided that the above
 copyright notice and this permission notice appear in all copies.

 THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

 */
package spinat.jettyorajson;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import oracle.jdbc.OracleCallableStatement;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleTypes;
import oracle.sql.ARRAY;

public final class ProcedureCaller {

    public static class ConversionException extends RuntimeException {

        public ConversionException(String bla) {
            super(bla);
        }
    }

    public static class Box<X> {

        public X value;

        public Box() {
            this.value = null;
        }

        public Box(X x) {
            this.value = x;
        }
    }

    private final OracleConnection connection;
    private String numberTableName = "NUMBER_ARRAY";
    private String varchar2TableName = "VARCHAR2_ARRAY";
    private String dateTableName = "DATE_ARRAY";

    // unfortunately the JDBC retrival of Array Descriptors does not care about
    // set current_schema = 
    // therefore we resolve the name and store schema.name in these fields
    private String effectiveNumberTableName = null;
    private String effectiveVarchar2TableName = null;
    private String effectiveDateTableName = null;

    public ProcedureCaller(OracleConnection connection) {
        this.connection = connection;
    }

    /**
     * @return the numberTableName
     */
    public String getNumberTableName() {
        return numberTableName;
    }

    /**
     * @param numberTableName the numberTableName to set
     */
    public void setNumberTableName(String numberTableName) {
        this.numberTableName = numberTableName;
        this.effectiveNumberTableName = null;
    }

    /**
     * @return the varchar2TableName
     */
    public String getVarchar2TableName() {
        return varchar2TableName;
    }

    /**
     * @param varchar2TableName the varchar2TableName to set
     */
    public void setVarchar2TableName(String varchar2TableName) {
        this.varchar2TableName = varchar2TableName;
        this.effectiveVarchar2TableName = null;
    }

    /**
     * @return the dateTableName
     */
    public String getDateTableName() {
        return dateTableName;
    }

    /**
     * @param dateTableName the dateTableName to set
     */
    public void setDateTableName(String dateTableName) {
        this.dateTableName = dateTableName;
        this.effectiveDateTableName = null;
    }

    /*
     DBMS_UTILITY.NAME_RESOLVE (
     name          IN  VARCHAR2, 
     context       IN  NUMBER,
     schema        OUT VARCHAR2, 
     part1         OUT VARCHAR2, 
     part2         OUT VARCHAR2,
     dblink        OUT VARCHAR2, 
     part1_type    OUT NUMBER, 
     object_number OUT NUMBER);
    
     5 - synonym
     7 - procedure (top level)
     8 - function (top level)
     9 - package
     */
    private static class ResolvedName {

        public final String schema;
        public final String part1;
        public final String part2;
        public final String dblink;
        public final int part1_type;
        public final BigInteger object_number;

        @Override
        public String toString() {
            return "" + schema + ", " + part1 + ", " + part2 + ", " + dblink + ", " + part1_type + ", " + object_number;
        }

        public ResolvedName(String schema,
                String part1,
                String part2,
                String dblink,
                int part1_type,
                BigInteger object_number) {
            this.schema = schema;
            this.part1 = part1;
            this.part2 = part2;
            this.dblink = dblink;
            this.part1_type = part1_type;
            this.object_number = object_number;
        }
    }

    private static ResolvedName resolveName(OracleConnection con, String name, boolean typeContext) throws SQLException {
        try (CallableStatement cstm = con.prepareCall(
                "begin dbms_utility.name_resolve(?,?,?,?,?,?,?,?);end;")) {
            cstm.setString(1, name);
            if (typeContext) {
                cstm.setInt(2, 7);
            } else {
                cstm.setInt(2, 1); // is context PL/SQL code 
            }
            cstm.registerOutParameter(3, Types.VARCHAR);
            cstm.registerOutParameter(4, Types.VARCHAR);
            cstm.registerOutParameter(5, Types.VARCHAR);
            cstm.registerOutParameter(6, Types.VARCHAR);
            cstm.registerOutParameter(7, Types.INTEGER);
            cstm.registerOutParameter(8, Types.NUMERIC);
            cstm.execute();
            String schema = cstm.getString(3);
            String part1 = cstm.getString(4);
            String part2 = cstm.getString(5);
            String dblink = cstm.getString(6);
            int part1_type = cstm.getInt(7);
            if (cstm.wasNull()) {
                part1_type = 0;
            }
            BigDecimal object_number = cstm.getBigDecimal(8);
            return new ResolvedName(schema, part1, part2, dblink, part1_type, object_number.toBigIntegerExact());
        }
    }

    private String computeEffectiveName(String name) throws SQLException {
        if (name.contains(".")) {
            return name;
        }
        ResolvedName rn = resolveName(connection, name, true);
        return rn.schema + "." + name;
    }

    // represents types from PL/SQL
    private static abstract class Type {

        public abstract String plsqlName();

        // the data is transfered to the database in three tables one for numbers
        // one for strings and one for dates. The ArgArrays contains this data.
        // this method copies the data for Object o into ArgArrays a based on
        // the type
        public abstract void fillArgArrays(ArgArrays a, Object o);

        // this is the inverse process . The result data is returned from 
        // the database in three tables. Reconstruct the objects from the 
        // data in the arrays based on the type
        public abstract Object readFromResArrays(ResArrays a);

        // generate the PL/SQL code to read the data for the arguments from the 
        // three PL/SQL arrays
        // when reading out tables we need index variables, to keep the index variables
        // distinct each gets its own number, we track that with AtomicInteger
        // there is no concurrency involved we just need a box with an integer
        // the generated pl/SQL block should only depend should depend deterministically
        // on the procedure arguments. We do not want to blow up the statement cache
        public abstract void genReadOutThing(StringBuilder sb, AtomicInteger counter, String target);

        // generate the PL/SQL code to write the data from the OUT and IN/OUT 
        // and the return value to the three arrays
        // the reason for the AtomicInteger is the same as above
        public abstract void genWriteThing(StringBuilder sb, AtomicInteger counter, String source);

    }

    // the PL/SQL standrad types, identfied by their name DATE, NUMBER
    private static class NamedType extends Type {

        String name; // number, integer ...

        @Override
        public String plsqlName() {
            if (this.name.equals("PL/SQL BOOLEAN")) {
                return "boolean";
            } else {
                return this.name;
            }
        }

        @Override
        public void fillArgArrays(ArgArrays a, Object o) {
            if (this.name.equals("NUMBER")
                    || this.name.equals("INTEGER")
                    || this.name.equals("BINARY_INTEGER")) {
                a.addNumber((Number) o);
            } else if (this.name.equals("PL/SQL BOOLEAN")) {
                if (o == null) {
                    a.addNumber(null);
                } else {
                    boolean b = (Boolean) o;
                    a.addNumber(b ? 1 : 0);
                }

            } else if (this.name.equals("DATE")) {
                java.util.Date d;
                if (o != null && o instanceof String) {
                    d = Util.stringToDate((String) o);
                } else {
                    d = (java.util.Date) o;
                }
                a.addDate(d);
            } else {
                throw new RuntimeException("unsupported named type");
            }
        }

        @Override
        public Object readFromResArrays(ResArrays a) {
            if (this.name.equals("NUMBER")
                    || this.name.equals("INTEGER")
                    || this.name.equals("BINARY_INTEGER")) {
                return a.readBigDecimal();
            } else if (this.name.equals("DATE")) {
                return Util.dateToString(a.readDate());
            } else if (this.name.equals("PL/SQL BOOLEAN")) {
                BigDecimal x = a.readBigDecimal();
                if (x == null) {
                    return null;
                } else {
                    return x.equals(BigDecimal.ONE);
                }
            } else {
                throw new RuntimeException("unsupported named type: " + this.name);
            }
        }

        @Override
        public void genWriteThing(StringBuilder sb, AtomicInteger counter, String source) {
            if (this.name.equals("NUMBER")
                    || this.name.equals("INTEGER")
                    || this.name.equals("BINARY_INTEGER")) {
                sb.append("an.extend; an(an.last):= " + source + ";\n");
            } else if (this.name.equals("DATE")) {
                sb.append("ad.extend; ad(ad.last):= " + source + ";\n");
            } else if (this.name.equals("PL/SQL BOOLEAN")) {
                sb.append("an.extend; an(an.last):= case when " + source + " then 1 when not " + source + " then 0 else null end;\n");
            } else {
                throw new RuntimeException("unsupported base type");
            }
        }

        @Override
        public void genReadOutThing(StringBuilder sb, AtomicInteger counter, String target) {
            if (this.name.equals("NUMBER")
                    || this.name.equals("INTEGER")
                    || this.name.equals("BINARY_INTEGER")) {
                sb.append(target).append(":= an(inn);inn:=inn+1;\n");
            } else if (this.name.equals("DATE")) {
                sb.append(target).append(":= ad(ind); ind := ind+1;\n");
            } else if (this.name.equals("PL/SQL BOOLEAN")) {
                sb.append(target).append(":= an(inn)=1; inn := inn+1;\n");
            } else {
                throw new RuntimeException("unsupported base type");
            }
        }
    }

    private static class Varchar2Type extends Type {

        String name; // varchar2, number, integer ...
        int size; // 0 if unbounded, i.e. as direct parameter

        @Override
        public String plsqlName() {
            if (size == 0) {
                return "varchar2(32767)";
            } else {
                return "varchar2(" + size + ")";
            }
        }

        @Override
        public void fillArgArrays(ArgArrays a, Object o) {
            String s = (String) o;
            if (s == null) {
                a.addString(s);
            } else {
                int allowed_size = this.size == 0 ? 32767 : this.size;
                if (s.length() <= allowed_size) {
                    a.addString(s);
                } else {
                    throw new ConversionException("string is to large, allowed are "
                            + allowed_size + ", given length " + s.length());
                }
            }
        }

        @Override
        public Object readFromResArrays(ResArrays a) {
            return a.readString();
        }

        @Override
        public void genWriteThing(StringBuilder sb, AtomicInteger counter, String source) {
            sb.append("av.extend; av(av.last) := " + source + ";\n");
        }

        @Override
        public void genReadOutThing(StringBuilder sb, AtomicInteger counter, String target) {
            sb.append(target).append(":= av(inv); inv := inv+1;\n");
        }
    }

    private static class Field {

        String name;
        Type type;
    }

    private static class RecordType extends Type {

        String owner;
        String package_;
        String name;
        ArrayList<Field> fields;

        @Override
        public String plsqlName() {
            return this.owner + "." + this.package_ + "." + this.name;
        }

        @Override
        public void fillArgArrays(ArgArrays a, Object o) {
            if (o instanceof Map) {
                Map m = (Map) o;
                for (Field f : this.fields) {
                    String fname = f.name.toLowerCase();
                    if (m.containsKey(fname)) {
                        Object x = m.get(fname);
                        f.type.fillArgArrays(a, x);
                    } else {
                        throw new ConversionException("slot not found: " + fname);
                    }
                }
            }
        }

        @Override
        public Object readFromResArrays(ResArrays a) {
            HashMap<String, Object> m = new HashMap<>();
            for (Field f : this.fields) {
                Object o = f.type.readFromResArrays(a);
                m.put(f.name.toLowerCase(), o);
            }
            return m;
        }

        @Override
        public void genWriteThing(StringBuilder sb, AtomicInteger counter, String source) {
            for (Field f : this.fields) {
                String a = source + "." + f.name;
                f.type.genWriteThing(sb, counter, a);
            }
        }

        @Override
        public void genReadOutThing(StringBuilder sb, AtomicInteger counter, String target) {
            for (Field f : this.fields) {
                String a = target + "." + f.name;
                f.type.genReadOutThing(sb, counter, a);
            }
        }
    }

    // type bla is table of blub;
    // no indexed by
    private static class TableType extends Type {

        String owner;
        String package_;
        String name;
        Type slottype;

        @Override
        public String plsqlName() {
            return this.owner + "." + this.package_ + "." + this.name;
        }

        @Override
        public void fillArgArrays(ArgArrays a, Object o) {
            if (o == null) {
                a.addNumber((BigDecimal) null);
            } else {
                ArrayList l = (ArrayList) o;
                a.addNumber(l.size());
                for (Object x : l) {
                    this.slottype.fillArgArrays(a, x);
                }
            }
        }

        @Override
        public Object readFromResArrays(ResArrays a) {
            BigDecimal b = a.readBigDecimal();
            if (b == null) {
                return null;
            } else {
                int size = b.intValue();
                ArrayList<Object> res = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    res.add(this.slottype.readFromResArrays(a));
                }
                return res;
            }
        }

        @Override
        public void genWriteThing(StringBuilder sb, AtomicInteger counter, String source) {
            sb.append("an.extend;\n");
            sb.append(" if " + source + " is null then\n");
            sb.append("    an(an.last) := null;\n");
            sb.append("else \n");
            sb.append("  an(an.last) := nvl(" + source + ".last, 0);\n");
            String index = "i" + counter.incrementAndGet();
            sb.append("for " + index + " in 1 .. nvl(" + source + ".last,0) loop\n");
            this.slottype.genWriteThing(sb, counter, source + "(" + index + ")");
            sb.append("end loop;\n");
            sb.append("end if;\n");
        }

        @Override
        public void genReadOutThing(StringBuilder sb, AtomicInteger counter, String target) {
            sb.append("size_ := an(inn); inn:= inn+1;\n");
            sb.append("if size_ is null then\n");
            sb.append("  ").append(target).append(":=null;\n");
            sb.append("else\n");
            sb.append(" ").append(target).append(" := new ")
                    .append(this.package_).append(".").append(this.name).append("();\n");
            String index = "i" + counter.incrementAndGet();
            String newTarget = target + "(" + index + ")";
            sb.append("  for ").append(index).append(" in 1 .. size_ loop\n");
            sb.append("" + target + ".extend();\n");
            this.slottype.genReadOutThing(sb, counter, newTarget);
            sb.append("end loop;\n");
            sb.append("end if;\n");
        }
    }

    private static class SysRefCursorType extends Type {

        // tricky : unlike for tables we do not know the size in advance
        // and we do not know the columns
        // thus when retrieving write the columns (name,type)
        //   and then write the rows
        // for a row write 1 into number and then the row data
        // if there is no more data then write 0 into number
        // support for clobs, cursor in result set?
        // maybe a procedure whcih reads out the data and returns 
        // a handle and an array (or just a string?) with the columns types for readput
        @Override
        public String plsqlName() {
            return "sys_refcursor";
        }

        @Override
        public void fillArgArrays(ArgArrays a, Object o) {
            throw new Error("sys_refcursor may not be an \"IN\" or \"IN OUT\" parameter");
        }

        @Override
        public Object readFromResArrays(ResArrays a) {
            int colcount = a.readBigDecimal().intValue();
            ArrayList<String> colnames = new ArrayList<>();
            ArrayList<String> coltypes = new ArrayList<>();
            for (int i = 0; i < colcount; i++) {
                colnames.add(a.readString());
                coltypes.add(a.readString());
            }
            ArrayList<HashMap<String, Object>> l = new ArrayList<>();
            while (true) {
                if (a.readBigDecimal().intValue() == 0) {
                    break;
                }
                HashMap<String, Object> m = new HashMap<>();
                for (int i = 0; i < colcount; i++) {
                    String t = coltypes.get(i);
                    final Object o;
                    if (t.equals("N")) {
                        o = a.readBigDecimal();
                    } else if (t.equals("V")) {
                        o = a.readString();
                    } else if (t.equals("D")) {
                        o = Util.dateToString(a.readDate());
                    } else {
                        throw new Error("unknwon column type: " + t);
                    }
                    m.put(colnames.get(i).toLowerCase(), o);
                }
                l.add(m);
            }
            return l;
        }

        @Override
        public void genReadOutThing(StringBuilder sb, AtomicInteger counter, String target) {
            throw new Error("sys_refcursor may not be an \"IN\" or \"IN OUT\" parameter");
        }

        @Override
        public void genWriteThing(StringBuilder sb, AtomicInteger counter, String source) {
            sb.append("declare h integer;\n");
            sb.append(" t varchar2(100);\n");
            sb.append(" rec_tab   DBMS_SQL.DESC_TAB;\n");
            sb.append(" rec       DBMS_SQL.DESC_REC;\n");
            sb.append(" x number;\n");
            sb.append("num number;\n");
            sb.append("dat date;\n");
            sb.append("varc varchar2(4000);\n");
            sb.append(" col_cnt integer;\n");
            sb.append("begin\n");
            sb.append(" h := DBMS_SQL.TO_CURSOR_NUMBER (" + source + ");\n");
            sb.append(" DBMS_SQL.DESCRIBE_COLUMNS(h, col_cnt, rec_tab);\n");
            sb.append(" an.extend; an(an.last):= col_cnt;\n");
            sb.append(" for i in 1 .. rec_tab.last loop\n");
            sb.append("  rec := rec_tab(i);\n");
            sb.append("  av.extend; av(av.last):= rec.col_name;\n");
            sb.append("if rec.col_type = dbms_types.TYPECODE_DATE then\n");
            sb.append("        dbms_sql.define_column(h, i, dat);\n");
            sb.append(" t:=t||'D';\n");
            sb.append("elsif rec.col_type = dbms_types.TYPECODE_NUMBER then\n");
            sb.append("        dbms_sql.define_column(h, i, num);\n");
            sb.append(" t:=t||'N';\n");
            sb.append("else\n");
            sb.append("        dbms_sql.define_column(h, i, varc, 4000);\n");
            sb.append(" t:=t||'V';\n");
            sb.append("end if;");
            sb.append("av.extend;av(av.last):=substr(t,i,1);\n");
            sb.append(" end loop;\n");
            sb.append(" loop\n");
            sb.append("      x := DBMS_SQL.FETCH_ROWS(h);\n");
            sb.append("      exit when x = 0;\n");
            sb.append("      an.extend; an(an.last):= 1\n;");
            sb.append("      for i in 1 .. col_cnt loop\n");
            sb.append("        case substr(t,i,1) \n");
            sb.append("         when 'D' then\n");
            sb.append("          DBMS_SQL.COLUMN_VALUE(h, i, dat);\n");
            sb.append("          ad.extend; ad(ad.last) := dat;\n");
            sb.append("        when 'N' then\n");
            sb.append("          DBMS_SQL.COLUMN_VALUE(h, i, num);\n");
            sb.append("          an.extend; an(an.last) := num;\n");
            sb.append("        when 'V' then\n");
            sb.append("          DBMS_SQL.COLUMN_VALUE(h, i, varc);\n");
            sb.append("          av.extend; av(av.last) := varc;\n");
            sb.append("         else raise_application_error(-20000,'BUG: unknown internal type code: '||t);\n");
            sb.append("         end case;\n");
            sb.append("      end loop;\n");
            sb.append("    end loop;\n");
            sb.append("      an.extend; an(an.last):= 0\n;");
            sb.append("end;");

        }
    }

    private static class TypedRefCursorType extends Type {

        String owner;
        String package_;
        String name;
        RecordType rectype;

        @Override
        public String plsqlName() {
            return "sys_refcursor";
        }

        @Override
        public void fillArgArrays(ArgArrays a, Object o) {
            throw new Error("ref cursor may not be an \"IN\" or \"IN OUT\" parameter");
        }

        @Override
        public Object readFromResArrays(ResArrays a) {
            ArrayList<HashMap<String, Object>> l = new ArrayList<>();
            while (true) {
                if (a.readBigDecimal().intValue() == 0) {
                    break;
                }
                HashMap<String, Object> m = (HashMap<String, Object>) rectype.readFromResArrays(a);
                l.add(m);
            }
            return l;
        }

        @Override
        public void genReadOutThing(StringBuilder sb, AtomicInteger counter, String target) {
            throw new Error("ref cursor may not be an \"IN\" or \"IN OUT\" parameter");
        }

        @Override
        public void genWriteThing(StringBuilder sb, AtomicInteger counter, String source) {
            sb.append("declare r " + rectype.plsqlName() + ";\n");
            sb.append("x integer;\n");
            sb.append("begin\n");
            sb.append("loop\n");
            sb.append(" fetch " + source + " into r;\n");
            sb.append("if " + source + "%notfound then\n");
            sb.append("  exit;\n");
            sb.append("end if;\n");
            sb.append("an.extend;an(an.last) := 1;\n");
            rectype.genWriteThing(sb, counter, "r");
            sb.append("end loop;\n");
            sb.append("an.extend;an(an.last) := 0;\n");
            sb.append("end;\n");
        }
    }

    // the arguments to a procedure/function
    private static class Argument {

        public String name;
        public String direction;
        public Type type;
    }

    // represents one procedure/function
    private static class Procedure {

        // not null if function
        Type returnType;
        String original_name;
        String owner;
        String package_; // could be null
        String name;
        int overload;
        ArrayList<Argument> arguments;
        // used to store the generated pl/sql block
        String plsqlstatement = null;
    }

    // this class corresponds 1:1 the columns in all_arguments, some columns are lft out
    // when working with the data in all_arguments it is transformed into an ArrayList
    // of this ArgumentsRow
    private static class ArgumentsRow {

        String owner;
        String object_name;
        String package_name;
        String argument_name;
        int position;
        int sequence;
        int data_level;
        String data_type;
        int overload;
        String in_out;
        String type_owner;
        String type_name;
        String type_subname;
        String pls_type;
        int data_length;
    }

    private static ArrayDeque<ArgumentsRow> fetchArgumentsRows(ResultSet rs) throws SQLException {
        ArrayDeque<ArgumentsRow> res = new ArrayDeque<>();
        while (rs.next()) {
            ArgumentsRow r = new ArgumentsRow();
            r.owner = rs.getString("OWNER");
            r.object_name = rs.getString("OBJECT_NAME");
            r.package_name = rs.getString("PACKAGE_NAME");
            r.argument_name = rs.getString("ARGUMENT_NAME");
            r.position = rs.getInt("POSITION");
            r.sequence = rs.getInt("SEQUENCE");
            r.data_level = rs.getInt("DATA_LEVEL");
            r.data_type = rs.getString("DATA_TYPE");
            r.overload = rs.getInt("OVERLOAD");
            if (rs.wasNull()) {
                r.overload = -1; //just not null
            }
            r.in_out = rs.getString("IN_OUT");
            r.type_owner = rs.getString("TYPE_OWNER");
            r.type_name = rs.getString("TYPE_NAME");
            r.type_subname = rs.getString("TYPE_SUBNAME");
            r.pls_type = rs.getString("PLS_TYPE");
            r.data_length = rs.getInt("DATA_LENGTH");
            if (rs.wasNull()) {
                r.data_length = 0;
            }
            res.add(r);
        }
        return res;
    }

    // get a Field from Args a and advance the internal position to the position
    // after this Field.
    // due to the recursive structure of PL/SQL types this method is recursive
    private static Field eatArg(ArrayDeque<ArgumentsRow> a) {
        ArgumentsRow r = a.getFirst();
        Field f = new Field();
        f.name = r.argument_name;
        if (r.data_type.equals("NUMBER")
                || r.data_type.equals("DATE")
                || r.data_type.equals("INTEGER")
                || r.data_type.equals("PL/SQL BOOLEAN")
                || r.data_type.equals("BINARY_INTEGER")) {
            NamedType t = new NamedType();
            t.name = r.data_type;
            f.type = t;
            a.pop();
            return f;
        }
        if (r.data_type.equals("VARCHAR2")) {
            Varchar2Type vt = new Varchar2Type();
            vt.name = "VARCHAR2";
            vt.size = r.data_length;
            f.type = vt;
            a.pop();
            return f;
        }
        if (r.data_type.equals("TABLE")) {
            TableType t = new TableType();
            t.owner = r.type_owner;
            t.package_ = r.type_name;
            t.name = r.type_subname;
            a.pop();
            Field f2 = eatArg(a);
            t.slottype = f2.type;
            f.type = t;
            return f;
        }
        if (r.data_type.equals("PL/SQL RECORD")) {
            RecordType t = new RecordType();
            t.owner = r.type_owner;
            t.package_ = r.type_name;
            t.name = r.type_subname;
            t.fields = new ArrayList<>();
            int level = r.data_level;
            a.pop();
            while (!a.isEmpty() && a.getFirst().data_level > level) {
                t.fields.add(eatArg(a));
            }
            f.type = t;
            return f;
        }
        // as of Oracle 11.2.0.2.0 ref cursors can not be part of records or be
        // part of a table
        if (r.data_type.equals("REF CURSOR")) {
            a.pop();
            if (a.isEmpty() || a.getFirst().data_level == 0) {
                SysRefCursorType t = new SysRefCursorType();
                f.type = t;
                return f;
            }
            TypedRefCursorType t = new TypedRefCursorType();
            t.owner = r.type_owner;
            t.package_ = r.type_name;
            t.name = r.type_subname;
            Field f2 = eatArg(a);
            if (f2.type instanceof RecordType) {
                t.rectype = (RecordType) f2.type;
                f.type = t;
                if (t.rectype.name == null) {
                    throw new RuntimeException("anonymous record types (%rowtype) are not supported");
                }
                return f;
            } else {
                throw new RuntimeException("unknown record type for cursor");
            }
        }
        throw new RuntimeException("unsupported type: " + r.data_type);
    }

    private static Procedure eatProc(ArrayDeque<ArgumentsRow> a) {
        Procedure p = new Procedure();
        ArgumentsRow r = a.getFirst();
        p.package_ = r.package_name;
        p.name = r.object_name;
        p.overload = r.overload;
        p.owner = r.owner;
        p.arguments = new ArrayList<>();
        if (a.getFirst().data_type == null) {
            // this is a procedure with no arguments
            a.pop();
            return p;
        }
        if (r.position == 0) {
            // this a function the return type is the first argument, the 
            // argument name is null
            Field f = eatArg(a);
            p.returnType = f.type;
        }
        while (!a.isEmpty() && a.getFirst().overload == p.overload) {
            String io = a.getFirst().in_out;
            Field f = eatArg(a);
            Argument ar = new Argument();
            ar.direction = io;
            ar.name = f.name;
            ar.type = f.type;
            p.arguments.add(ar);
        }
        return p;
    }

    private static class ArgArrays {

        ArrayList<BigDecimal> decimal = new ArrayList<>();
        ArrayList<String> varchar2 = new ArrayList<>();
        ArrayList<java.sql.Timestamp> date = new ArrayList<>();

        public void addNumber(Number n) {
            if (n == null) {
                this.decimal.add(null);
            } else if (n instanceof Integer) {
                this.decimal.add(BigDecimal.valueOf((Integer) n));
            } else if (n instanceof Long) {
                this.decimal.add(BigDecimal.valueOf((Long) n));
            } else if (n instanceof BigInteger) {
                this.decimal.add(new BigDecimal((BigInteger) n));
            } else if (n instanceof BigDecimal) {
                this.decimal.add((BigDecimal) n);
            } else if (n instanceof Double) {
                this.decimal.add(BigDecimal.valueOf((Double) n));
            } else {
                throw new RuntimeException("unsupported number type");
            }
        }

        public void addString(String s) {
            this.varchar2.add(s);
        }

        public void addDate(java.util.Date d) {
            if (d == null) {
                this.date.add(null);
            } else {
                this.date.add(new Timestamp(d.getTime()));
            }
        }
    }

    private static class ResArrays {

        ArrayList<BigDecimal> decimal = new ArrayList<>();
        ArrayList<String> varchar2 = new ArrayList<>();
        ArrayList<java.sql.Timestamp> date = new ArrayList<>();

        int posd = 0;
        int posv = 0;
        int posdate = 0;

        public String readString() {
            String res = varchar2.get(posv);
            posv++;
            return res;
        }

        public BigDecimal readBigDecimal() {
            BigDecimal res = decimal.get(posd);
            posd++;
            return res;
        }

        public java.util.Date readDate() {
            Timestamp ts = this.date.get(posdate);
            posdate++;
            if (ts == null) {
                return null;
            }
            return new java.util.Date(ts.getTime());
        }
    }

    private String createStatementString(Procedure p) {
        StringBuilder sb = new StringBuilder();
        sb.append("declare\n");
        sb.append("an " + this.numberTableName + ";\n");
        sb.append("av " + this.varchar2TableName + " ;\n");
        sb.append("ad " + this.dateTableName + ";\n");
        sb.append("inn integer :=1;\n");
        sb.append("inv integer :=1;\n");
        sb.append("ind integer :=1;\n");
        sb.append("size_ integer;");
        if (p.returnType != null) {
            sb.append("result$ ").append(p.returnType.plsqlName()).append(";\n");
        }
        for (int i = 0; i < p.arguments.size(); i++) {
            sb.append("p" + i + "$ ").append(p.arguments.get(i).type.plsqlName());
            sb.append(";\n");
        }
        sb.append("begin\n");
        sb.append("dbms_output.put_line('a '||to_char(sysdate,'mi:ss'));\n");
        sb.append("an :=?;\n");
        sb.append("av :=?;\n");
        sb.append("ad :=?;\n");
        sb.append("dbms_output.put_line('b '||to_char(sysdate,'mi:ss'));\n");
        AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < p.arguments.size(); i++) {
            Argument a = p.arguments.get(i);
            if (a.direction.equals("OUT")) {
                continue;
            }
            a.type.genReadOutThing(sb, counter, "p" + i + "$");
        }
        sb.append("dbms_output.put_line('c '||to_char(sysdate,'mi:ss'));\n");
        sb.append("an:= " + this.numberTableName + "();\n");
        sb.append("av:= " + this.varchar2TableName + "();\n");
        sb.append("ad:= " + this.dateTableName + "();\n");
        if (p.returnType != null) {
            sb.append("result$:=");
        }
        sb.append(p.original_name + "(");
        for (int i = 0; i < p.arguments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(" " + p.arguments.get(i).name + " => ");
            sb.append("p" + i + "$");
        }
        sb.append(");\n");
        sb.append("dbms_output.put_line('d '||to_char(sysdate,'mi:ss'));\n");
        if (p.returnType != null) {
            p.returnType.genWriteThing(sb, counter, "result$");
        }
        for (int i = 0; i < p.arguments.size(); i++) {
            Argument a = p.arguments.get(i);
            if (a.direction.equals("IN")) {
                continue;
            }
            a.type.genWriteThing(sb, counter, "p" + i + "$");
        }
        sb.append("dbms_output.put_line('e '||to_char(sysdate,'mi:ss'));\n");
        sb.append("?:= an;\n");
        sb.append("?:= av;\n");
        sb.append("?:= ad;\n");
        sb.append("dbms_output.put_line('f '||to_char(sysdate,'mi:ss'));\n");
        sb.append("end;\n");
        return sb.toString();
    }

    private Map<String, Object> call(
            Procedure p, Map<String, Object> args) throws SQLException {
        if (p.plsqlstatement == null) {
            p.plsqlstatement = createStatementString(p);
        }
        if (this.effectiveNumberTableName == null) {
            this.effectiveNumberTableName = computeEffectiveName(this.numberTableName);
        }
        if (this.effectiveVarchar2TableName == null) {
            this.effectiveVarchar2TableName = computeEffectiveName(this.varchar2TableName);
        }
        if (this.effectiveDateTableName == null) {
            this.effectiveDateTableName = computeEffectiveName(this.dateTableName);
        }

        final ARRAY no;
        final ARRAY vo;
        final ARRAY do_;
        try (OracleCallableStatement cstm = (OracleCallableStatement) this.connection.prepareCall(p.plsqlstatement)) {
            ArgArrays aa = new ArgArrays();
            for (Argument arg : p.arguments) {
                if (arg.direction.equals("OUT")) {
                    continue;
                }
                String arg_name = arg.name.toLowerCase();
                if (args.containsKey(arg_name)) {
                    Object o = args.get(arg_name);
                    arg.type.fillArgArrays(aa, o);
                } else {
                    throw new ConversionException("could not find argument " + arg_name);
                }
            }

            cstm.setArray(1, (oracle.sql.ARRAY) this.connection.createARRAY(this.effectiveNumberTableName, aa.decimal.toArray(new BigDecimal[0])));
            cstm.setArray(2, (oracle.sql.ARRAY) this.connection.createARRAY(this.effectiveVarchar2TableName, aa.varchar2.toArray(new String[0])));
            cstm.setArray(3, (oracle.sql.ARRAY) this.connection.createARRAY(this.effectiveDateTableName, aa.date.toArray(new Timestamp[0])));

            cstm.registerOutParameter(4, OracleTypes.ARRAY, this.effectiveNumberTableName);
            cstm.registerOutParameter(5, OracleTypes.ARRAY, this.effectiveVarchar2TableName);
            cstm.registerOutParameter(6, OracleTypes.ARRAY, this.effectiveDateTableName);
            cstm.execute();
            no = cstm.getARRAY(4);
            vo = cstm.getARRAY(5);
            do_ = cstm.getARRAY(6);
        }
        ResArrays ra = new ResArrays();

        for (Object o : (Object[]) no.getArray()) {
            ra.decimal.add((BigDecimal) o);
        }

        for (Object o : (Object[]) vo.getArray()) {
            ra.varchar2.add((String) o);
        }
        for (Object o : (Object[]) do_.getArray()) {
            ra.date.add((Timestamp) o);
        }
        HashMap<String, Object> res = new HashMap<>();
        if (p.returnType != null) {
            Object o = p.returnType.readFromResArrays(ra);
            res.put("RETURN", o);
        }
        for (Argument arg : p.arguments) {
            if (arg.direction.equals("IN")) {
                continue;
            }
            Object o = arg.type.readFromResArrays(ra);
            res.put(arg.name.toLowerCase(), o);
        }
        return res;
    }

    // call the procedure, for each parameter there must be an entry in Object
    private Object callPositional(Procedure p, Object[] args) throws SQLException {
        if (p.plsqlstatement == null) {
            p.plsqlstatement = createStatementString(p);
        }
        if (this.effectiveNumberTableName == null) {
            this.effectiveNumberTableName = computeEffectiveName(this.numberTableName);
        }
        if (this.effectiveVarchar2TableName == null) {
            this.effectiveVarchar2TableName = computeEffectiveName(this.varchar2TableName);
        }
        if (this.effectiveDateTableName == null) {
            this.effectiveDateTableName = computeEffectiveName(this.dateTableName);
        }
        if (p.arguments.size() > args.length) {
            throw new RuntimeException("not enough arguments supplied");
        }
        if (p.arguments.size() < args.length) {
            throw new RuntimeException("too many arguments supplied");
        }

        final ARRAY no;
        final ARRAY vo;
        final ARRAY do_;
        try (OracleCallableStatement cstm = (OracleCallableStatement) this.connection.prepareCall(p.plsqlstatement)) {
            ArgArrays aa = new ArgArrays();
            {
                int i = 0;
                for (Argument arg : p.arguments) {
                    if (!arg.direction.equals("OUT")) {
                        Object o = args[i];
                        if (o instanceof Box) {
                            o = ((Box) o).value;
                        }
                        arg.type.fillArgArrays(aa, o);
                    }
                    i++;
                }
            }
            cstm.setArray(1, (oracle.sql.ARRAY) this.connection.createARRAY(this.effectiveNumberTableName, aa.decimal.toArray(new BigDecimal[0])));
            cstm.setArray(2, (oracle.sql.ARRAY) this.connection.createARRAY(this.effectiveVarchar2TableName, aa.varchar2.toArray(new String[0])));
            cstm.setArray(3, (oracle.sql.ARRAY) this.connection.createARRAY(this.effectiveDateTableName, aa.date.toArray(new Timestamp[0])));

            cstm.registerOutParameter(4, OracleTypes.ARRAY, this.effectiveNumberTableName);
            cstm.registerOutParameter(5, OracleTypes.ARRAY, this.effectiveVarchar2TableName);
            cstm.registerOutParameter(6, OracleTypes.ARRAY, this.effectiveDateTableName);
            cstm.execute();
            no = cstm.getARRAY(4);
            vo = cstm.getARRAY(5);
            do_ = cstm.getARRAY(6);
        }
        ResArrays ra = new ResArrays();

        for (Object o : (Object[]) no.getArray()) {
            ra.decimal.add((BigDecimal) o);
        }

        for (Object o : (Object[]) vo.getArray()) {
            ra.varchar2.add((String) o);
        }

        for (Object o : (Object[]) do_.getArray()) {
            ra.date.add((Timestamp) o);
        }
        Object result;
        if (p.returnType != null) {
            result = p.returnType.readFromResArrays(ra);
        } else {
            result = null;
        }
        {
            int i = 0;
            for (Argument arg : p.arguments) {
                if (!arg.direction.equals("IN")) {

                    if (args[i] != null && args[i] instanceof Box) {
                        Object o = arg.type.readFromResArrays(ra);
                        ((Box) args[i]).value = o;
                    } else {
                        throw new RuntimeException("need a box for parameter " + arg.name);
                    }
                }
                i++;
            }
        }
        return result;
    }

    private static String sql1 = "select OWNER,OBJECT_NAME,PACKAGE_NAME,ARGUMENT_NAME,\n"
            + "POSITION,SEQUENCE,DATA_LEVEL,DATA_TYPE,\n"
            + " OVERLOAD, IN_OUT, TYPE_OWNER, TYPE_NAME, TYPE_SUBNAME, PLS_TYPE,data_length\n"
            + " from all_arguments \n"
            + " where owner = ? and package_name = ? and object_name = ?\n"
            + " order by owner,package_name,object_name,overload,sequence";

    private static String sql2 = "select OWNER,OBJECT_NAME,PACKAGE_NAME,ARGUMENT_NAME,"
            + "POSITION,SEQUENCE,DATA_LEVEL,DATA_TYPE,"
            + " OVERLOAD, IN_OUT, TYPE_OWNER, TYPE_NAME, TYPE_SUBNAME, PLS_TYPE,data_length\n"
            + " from all_arguments \n"
            + " where object_id = ? \n"
            + " order by owner,package_name,object_name,overload,sequence";

    private ArrayList<Procedure> getProcsFromDB(String name) throws SQLException {

        ResolvedName rn = resolveName(this.connection, name, false);
        if (rn.dblink != null) {
            throw new RuntimeException("no call over dblink");
        }
        ArrayDeque<ArgumentsRow> argument_rows;
        PreparedStatement pstm;

        if (rn.part1_type == 7 || rn.part1_type == 8) {
            // this a global procedure or function
            pstm = this.connection.prepareCall(sql2);
            pstm.setBigDecimal(1, new BigDecimal(rn.object_number));
        } else if (rn.part1_type == 9) {
            if (rn.part2 == null) {
                throw new RuntimeException("only package given: " + name);
            }
            // this is procedure or function in a package
            pstm = this.connection.prepareCall(sql1);
            pstm.setString(1, rn.schema);
            pstm.setString(2, rn.part1);
            pstm.setString(3, rn.part2);
        } else {
            throw new RuntimeException("this is not a gobal procedure/function, "
                    + "nor a procedure/function in a package: " + name);
        }
        try (ResultSet rs = pstm.executeQuery()) {
            argument_rows = fetchArgumentsRows(rs);
            rs.close();
        }
        pstm.close();
        if (argument_rows.isEmpty()) {
            throw new RuntimeException("procedure in package does not exist or object is not valid: " + name);
        }
        ArrayList<Procedure> procs = new ArrayList<>();
        while (!argument_rows.isEmpty()) {
            Procedure p = eatProc(argument_rows);
            p.original_name = name;
            procs.add(p);
        }
        return procs;
    }

    private Map<String, ArrayList<Procedure>> procsMap = new HashMap<>();

    private ArrayList<Procedure> getProcs(String name) throws SQLException {
        ArrayList<Procedure> procs = procsMap.get(name);
        if (procs == null) {
            procs = getProcsFromDB(name);
            procsMap.put(name, procs);
        }
        return procs;
    }

    public Map<String, Object> call(
            String name, int overload, Map<String, Object> args) throws SQLException {
        ArrayList<Procedure> procs = getProcs(name);

        if (overload > procs.size()) {
            throw new RuntimeException("the overload does not exist for procedure/function " + name);
        }
        if (overload <= 0) {
            throw new RuntimeException("overload must greater or equal 1");
        }
        return call(procs.get(overload - 1), args);
    }

    public Map<String, Object> call(
            String name, Map<String, Object> args) throws SQLException {
        ArrayList<Procedure> procs = getProcs(name);
        if (procs.size() > 1) {
            throw new RuntimeException("procedure/function is overloaded, supply a overload: " + name);
        } else {
            return this.call(procs.get(0), args);
        }
    }

    public Object callPositional(String name, Object... args) throws SQLException {
        ArrayList<Procedure> procs = getProcs(name);
        if (procs.size() > 1) {
            throw new RuntimeException("procedure/function is overloaded, supply a overload: " + name);
        } else {
            return this.callPositional(procs.get(0), args);
        }
    }

    public Object callPositionalO(String name, int overload, Object... args) throws SQLException {
        ArrayList<Procedure> procs = getProcs(name);
        if (overload > procs.size()) {
            throw new RuntimeException("the overload does not exist for procedure/function " + name);
        }
        if (overload <= 0) {
            throw new RuntimeException("overload must greater or equal 1");
        }
        return this.callPositional(procs.get(overload - 1), args);
    }
}
