import com.google.common.collect.Maps;
import com.microsoft.z3.*;
import org.ethereum.util.ByteUtil;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class Z3Test {
    @Before
    public void setup() throws IllegalAccessException, NoSuchFieldException {
        System.setProperty("java.library.path", "z3/lib");
        Field sys_paths = ClassLoader.class.getDeclaredField("sys_paths");
        sys_paths.setAccessible(true);
        sys_paths.set(null, null);
    }

    @Test
    public void testBV() {
        Context context = new Context();
        BitVecExpr a = context.mkBVConst("a", 256);
        BitVecExpr one = context.mkBV(1, 256);
        BitVecExpr a2 = context.mkBVAdd(a, one);
        BitVecExpr zero = context.mkBV(0, 256);
        BoolExpr b = context.mkEq(a2, zero);
        Solver solver = context.mkSolver();
        // BoolExprしかaddできない
        solver.add(b);
        if (solver.check() == Status.SATISFIABLE) {
            Model model = solver.getModel();
            BitVecNum result = (BitVecNum) model.eval(a, false);
            System.out.println(Hex.toHexString(result.getBigInteger().toByteArray()));
        }
    }

    @Test
    public void test_SimplifyはBitVecNumを返す() {
        Context context = new Context();
        BitVecNum one = context.mkBV(1, 256);
        BitVecNum two = context.mkBV(2, 256);
        BitVecExpr three = context.mkBVAdd(one, two);
        System.out.println(three instanceof BitVecNum); // false
        System.out.println(three.simplify() instanceof BitVecNum); // true
    }

    @Test
    public void test_BitVecNumのgetBigIntegerのtoStringは先頭の0を省略する() {
        Context context = new Context();
        BitVecNum a = context.mkBV(0x00ff, 16);
        assertEquals("ff", a.getBigInteger().toString(16));
    }

    @Test
    public void test_SimplifyしてもConstの順番は変わらない() {
        Context context = new Context();
        BitVecExpr a = context.mkBVConst("a", 256);
        BitVecExpr b = context.mkBVConst("b", 256);
        BitVecExpr c = context.mkBVConst("c", 256);
        BitVecExpr pattern1 = context.mkBVAdd(context.mkBVAdd(a, b), c);
        BitVecExpr pattern2 = context.mkBVAdd(context.mkBVAdd(c, b), a);
        System.out.println(pattern1.toString());
        System.out.println(pattern2.toString());
        System.out.println(pattern1.simplify().toString());
        System.out.println(pattern2.simplify().toString());
    }

    @Test
    public void test_ArrayConstはdataの再現に使えない() { // 範囲値を取ることできない
        Context context = new Context();
        ArrayExpr data = context.mkArrayConst("data", context.mkIntSort(), context.mkBitVecSort(8));

        // storeは配列への代入
        data = context.mkStore(data, context.mkInt(1), context.mkBV(2, 8));
        data = context.mkStore(data, context.mkInt(3), context.mkBV(4, 8));
        IntExpr j = context.mkInt(3); // context.mkIntConst("j");

        // selectは配列からの取得
        Expr d_j = context.mkSelect(data, j);

        BoolExpr b = context.mkEq(d_j, context.mkBV(4, 8));
//        BoolExpr b = context.mkEq(d_j, context.mkBV(5, 8));
        Solver solver = context.mkSolver();
        solver.add(b);
        if (solver.check() == Status.SATISFIABLE) {
            Model model = solver.getModel();
            System.out.println(model.eval(j, false));
            System.out.println(model.eval(data, false));
        } else {
            System.out.println("unsat");
        }
    }

    @Test
    public void test_ArrayConstはstorageの再現に使えない() { // 範囲値を取ることできない
        Context context = new Context();
        ArrayExpr storage = context.mkArrayConst("storage", context.mkBitVecSort(256), context.mkBitVecSort(256));

        BitVecExpr i = context.mkBVConst("i", 256);
        BitVecExpr j = context.mkBVConst("j", 256);
        // storeは配列への代入
        storage = context.mkStore(storage, i, context.mkBV(2, 256));
        storage = context.mkStore(storage, context.mkBV(1, 256), j);

        // selectは配列からの取得
        Expr s_i = context.mkSelect(storage, i);

        BoolExpr b = context.mkEq(s_i, j);
        Solver solver = context.mkSolver();
        solver.add(b);
        if (solver.check() == Status.SATISFIABLE) {
            Model model = solver.getModel();
            System.out.println(model.eval(i, false));
            System.out.println(model.eval(j, false));
            System.out.println(model.eval(storage, false));
        } else {
            System.out.println("unsat");
        }
    }

    //        context.mkZeroExt()
    @Test
    public void test_mkZeroExtでBVのbit数を増やすことができる() {
        Context context = new Context();
        BitVecExpr a = context.mkBVConst("a", 1024);
        BitVecExpr one = context.mkBV("1", 256);
        one = context.mkZeroExt(1024 - 256, one);
        assertFalse(one instanceof BitVecNum);
        // mkExtract後はsimplifyしないとBitVecNumにならない
        assertTrue(one.simplify() instanceof BitVecNum);
        BitVecExpr add = context.mkBVAdd(a, one);
        BoolExpr b = context.mkEq(add, context.mkBV(0, 1024));
        Solver solver = context.mkSolver();
        solver.add(b);
        if (solver.check() == Status.SATISFIABLE) {
            Model model = solver.getModel();
            System.out.println(model.eval(a, false));
        } else {
            System.out.println("unsat");
        }
    }

    //        context.mkZeroExt()
    @Test
    public void test_mkExtractでBVのbit数を減らすことができる() {
        Context context = new Context();
        BitVecExpr a = context.mkBVConst("a", 256);
        BitVecExpr one = context.mkBV("1", 1024);
        one = context.mkExtract(256 - 1, 0, one);
        assertFalse(one instanceof BitVecNum);
        // mkExtract後はsimplifyしないとBitVecNumにならない
        assertTrue(one.simplify() instanceof BitVecNum);
        BitVecExpr add = context.mkBVAdd(a, one);
        BoolExpr b = context.mkEq(add, context.mkBV(0, 256));
        Solver solver = context.mkSolver();
        solver.add(b);
        if (solver.check() == Status.SATISFIABLE) {
            Model model = solver.getModel();
            System.out.println(model.eval(a, false));
        } else {
            System.out.println("unsat");
        }
    }


    @Test
    public void test_mkExtractでBYTEを再現できない() { // 引数がint型
        Context context = new Context();
        BitVecNum a = context.mkBV(Long.parseLong("0123456789ABCDEF", 16), 8 * 8);
        System.out.println(Long.toHexString(a.getLong()));
        // mkExtract(endInclude, startInclude, expr)
        BitVecNum b = (BitVecNum) context.mkExtract(8 * 8 - 1, 0, a).simplify();
        System.out.println(Long.toHexString(b.getLong()));
    }


    @Test
    public void test_BitVecExprのbit数を取得する() {
        Context context = new Context();
        BitVecExpr a = context.mkBVConst("a", 1024);
        System.out.println(a.getSortSize());
    }

    @Test
    public void test_符号付き空bytesのBigIntegerはtoStrringで0になる() {
        assertEquals("0", new BigInteger(1, new byte[0]).toString());
    }

    @Test
    public void test_BitVecNumのtoStringは数字() {
        Context context = new Context();
        BitVecExpr a = context.mkBV("1234567890987654321", 1024);
        System.out.println(a.toString());
    }

    @Test
    public void test_solverにaddしてsimplyfyしてもBitVecNumberにはならない() {
        // addしてcheckしてgetModelしないと値は得られない。
        Context context = new Context();
        Solver solver = context.mkSolver();
        BitVecExpr a = context.mkBVConst("a", 1024);
        System.out.println(a.simplify());
        solver.add(context.mkEq(a, context.mkBV("123", 1024)));
        System.out.println(a.simplify());
        if (solver.check() == Status.SATISFIABLE) {
            Model model = solver.getModel();
            System.out.println(model.evaluate(a, false).simplify());
        }
    }

    @Test
    public void test_別contextにaddするBoolExprをtrasnslateコピーできる() {
        Context ctx1 = new Context();
        BitVecExpr a = ctx1.mkBVConst("a", 256);
        BitVecExpr one = ctx1.mkBV("1", 256);
        BitVecExpr add = ctx1.mkBVAdd(a, one);
        BoolExpr constraint = ctx1.mkEq(add, ctx1.mkBV(0, 256));
        Solver solver1 = ctx1.mkSolver();
        solver1.add(constraint);
        if (solver1.check() == Status.SATISFIABLE) {
            Model model = solver1.getModel();
            System.out.println(model.eval(a, false));
        } else {
            System.out.println("unsat");
        }

        Context ctx2 = new Context();
        Solver solver2 = ctx2.mkSolver();
        // 別contextにaddするExprはtranslateしておく
        BoolExpr constraint_copy = (BoolExpr) constraint.translate(ctx2);
        solver2.add(constraint_copy);
        // 追加もできる
        BitVecExpr b = ctx2.mkBVConst("b", 256);
        BitVecExpr two = ctx2.mkBV("2", 256);
        BitVecExpr add2 = ctx2.mkBVAdd(b, two);
        BoolExpr constraint2 = ctx2.mkEq(add2, ctx2.mkBV(0, 256));
        solver2.add(constraint2);
        if (solver2.check() == Status.SATISFIABLE) {
            Model model = solver2.getModel();
            // addしたBoolExprの依存するExprは、translateしないと値を見れない
            System.out.println(model.eval(a, false));
            BitVecExpr a2 = (BitVecExpr) a.translate(ctx2);
            System.out.println(model.eval(a2, false));
            System.out.println(model.eval(b, false));
        } else {
            System.out.println("unsat");
        }
    }

    @Test
    public void test_別contextにsolver自体をコピーできる() {
        Context ctx1 = new Context();
        BitVecExpr a = ctx1.mkBVConst("a", 256);
        BitVecExpr one = ctx1.mkBV("1", 256);
        BitVecExpr add = ctx1.mkBVAdd(a, one);
        BoolExpr constraint = ctx1.mkEq(add, ctx1.mkBV(0, 256));
        Solver solver1 = ctx1.mkSolver();
        solver1.add(constraint);
        if (solver1.check() == Status.SATISFIABLE) {
            Model model = solver1.getModel();
            System.out.println(model.eval(a, false));
        } else {
            System.out.println("unsat");
        }

        Context ctx2 = new Context();
        Solver solver2 = solver1.translate(ctx2);
        // 追加もできる
        BitVecExpr b = ctx2.mkBVConst("b", 256);
        BitVecExpr two = ctx2.mkBV("2", 256);
        BitVecExpr add2 = ctx2.mkBVAdd(b, two);
        BoolExpr constraint2 = ctx2.mkEq(add2, ctx2.mkBV(0, 256));
        solver2.add(constraint2);
        if (solver2.check() == Status.SATISFIABLE) {
            Model model = solver2.getModel();
            // addしたBoolExprの依存するExprは、translateして値を見る
            System.out.println(model.eval(a, false));
            BitVecExpr a2 = (BitVecExpr) a.translate(ctx2);
            System.out.println(model.eval(a2, false));
            System.out.println(model.eval(b, false));
        } else {
            System.out.println("unsat");
        }
    }

    @Test
    public void test_addしたBoolExprをgetAssertionsで取ることができる() {
        Context ctx1 = new Context();
        BitVecExpr a = ctx1.mkBVConst("a", 256);
        BitVecExpr one = ctx1.mkBV("1", 256);
        BitVecExpr add = ctx1.mkBVAdd(a, one);
        BoolExpr b = ctx1.mkEq(add, ctx1.mkBV(0, 256));
        System.out.println(b);
        Solver solver1 = ctx1.mkSolver();
        solver1.add(b);
        System.out.println(solver1.getAssertions().length);
        System.out.println(solver1.getAssertions()[0]);
    }

    @Test
    public void test_BitVecNumで正の数の31ビットはgetIntできる() {
        Context context = new Context();
        BitVecNum num = context.mkBV(1, 31);
        assertEquals(1, num.getInt());
    }


    @Test
    public void test_BitVecNumで負の数の31ビットはgetIntできる() {
        Context context = new Context();
        BitVecNum num = context.mkBV(-1, 31);
        assertEquals(0x7fffffff, num.getInt());
    }

    @Test
    public void test_BitVecNumで正の数の32ビットはgetIntできる() {
        Context context = new Context();
        BitVecNum num = context.mkBV(1, 32);
        assertEquals(1, num.getInt());
    }


    @Test(expected = Z3Exception.class)
    public void test_BitVecNumで負の数の32ビットはgetIntできない() {
        Context context = new Context();
        BitVecNum num = context.mkBV(-1, 32);
        // getIntは負の数を返すことができない
        num.getInt();
    }

    @Test
    public void test_BitVecNumで正の数の63ビットはgetLongできる() {
        Context context = new Context();
        BitVecNum num = context.mkBV(1, 63);
        assertEquals(1, num.getLong());
    }


    @Test
    public void test_BitVecNumで負の数の63ビットはgeLongできる() {
        Context context = new Context();
        BitVecNum num = context.mkBV(-1, 63);
        assertEquals(0x7fffffffffffffffL, num.getLong());
    }

    @Test
    public void test_BitVecNumでgetLong正の数の64ビットはgetLongできる() {
        Context context = new Context();
        BitVecNum num = context.mkBV(1, 64);
        assertEquals(1, num.getLong());
    }


    @Test(expected = Z3Exception.class)
    public void test_BitVecNumで負の数の64ビットはgetLongできない() {
        Context context = new Context();
        BitVecNum num = context.mkBV(-1, 64);
        // getLongは負の数を返すことができない
        num.getLong();
    }

    @Test(expected = Z3Exception.class)
    public void test_BitVecNumで0bitは作れない() {
        Context context = new Context();
        BitVecNum num = context.mkBV(0, 0);
    }

    @Test(expected = Z3Exception.class)
    public void test_solverをtranslateしても別コンテキストで作ったBVConstはmkBVAddできない() {
        Context ctx1 = new Context();

        // 以下はstackにある想定
        BitVecExpr b = ctx1.mkBVConst("b", 256);
        BitVecExpr two = ctx1.mkBV("2", 256);

        BitVecExpr a = ctx1.mkBVConst("a", 256);
        BitVecExpr one = ctx1.mkBV("1", 256);
        BitVecExpr a_plus_one = ctx1.mkBVAdd(a, one);
        BoolExpr constraint = ctx1.mkEq(a_plus_one, ctx1.mkBV(0, 256));
        Solver solver1 = ctx1.mkSolver();
        solver1.add(constraint);

        Context ctx2 = new Context();
        // translate
        Solver solver2 = solver1.translate(ctx2);
        // mkBVAdd
        BitVecExpr b_plus_two = ctx2.mkBVAdd(b, two);
//        BoolExpr constraint2 = ctx2.mkEq(b_plus_two, ctx2.mkBV(0, 256));
//        solver2.add(constraint2);
//        if (solver2.check() == Status.SATISFIABLE) {
//            System.out.println("sat");
//        } else {
//            System.out.println("unsat");
//        }
    }

    @Test(expected = Z3Exception.class)
    public void test_solverをtranslateしても別コンテキストで作ったconstraintに含まれるBVConstもmkBVAddできない() {
        Context ctx1 = new Context();

        // 以下はstackにある想定
        BitVecExpr b = ctx1.mkBVConst("b", 256);
        BitVecExpr two = ctx1.mkBV("2", 256);

        BitVecExpr a = ctx1.mkBVConst("a", 256);
        BitVecExpr one = ctx1.mkBV("1", 256);
        BitVecExpr a_plus_one = ctx1.mkBVAdd(a, one);
        BoolExpr constraint = ctx1.mkEq(a_plus_one, ctx1.mkBV(0, 256));
        Solver solver1 = ctx1.mkSolver();
        solver1.add(constraint);

        Context ctx2 = new Context();
        // translate
        Solver solver2 = solver1.translate(ctx2);
        // mkBVAdd
        BitVecExpr a_plus_two = ctx2.mkBVAdd(a, two);
    }

    @Test
    public void test_translateすると別オブジェクトになる() {
        Context ctx1 = new Context();
        BitVecExpr a = ctx1.mkBVConst("a", 256);
        Context ctx2 = new Context();
        BitVecExpr a2 = (BitVecExpr) a.translate(ctx2);
        assertNotEquals(a, a2);
        assertEquals(a.toString(), a2.toString());
    }


    @Test
    public void test_HashMapのtoStringは再帰的にtoStringする() {
        Map<String, BitVecExpr> map = Maps.newHashMap();
        Context ctx1 = new Context();
        BitVecExpr a = ctx1.mkBVConst("a", 256);
        map.put("a", a);
        BitVecExpr one = ctx1.mkBV("1", 256);
        map.put("1", one);

        Map<String, BitVecExpr> map2 = Maps.newHashMap();
        Context ctx2 = new Context();
        BitVecExpr a2 = (BitVecExpr) a.translate(ctx2);
        map2.put("a", a2);
        BitVecExpr one2 = (BitVecExpr) one.translate(ctx2);
        map2.put("1", one2);

        assertNotEquals(map, map2);
        System.out.println(map.toString());
        assertEquals(map.toString(), map2.toString());
    }


    @Test
    public void test_別スレッドでnewしたContextを利用できる() throws InterruptedException {
        Context context = new Context();
        Solver solver = context.mkSolver();
        final Thread thread = new Thread(() -> {
            System.out.println("Context");
            BitVecExpr a = context.mkBVConst("a", 256);
            BitVecExpr one = context.mkBV("1", 256);
            BitVecExpr add = context.mkBVAdd(a, one);
            BoolExpr b = context.mkEq(add, context.mkBV(0, 256));
            solver.add(b);
            solver.check();
            Model model = solver.getModel();
            System.out.println(model.eval(a, false));
        });
        thread.start();
        thread.join();
    }


//    @Test
//    public void test_別スレッドかつ別Contextで同時にContextとSolverを作ることができる() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                Solver solver1 = ctx1.mkSolver();
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//
//    @Test
//    public void test_別スレッドかつ別Contextで同時にModelを作ることはできない() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                Solver solver1 = ctx1.mkSolver();
//                Model model = solver1.getModel();
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//
//    @Test
//    public void test_別スレッドかつ別Contextであっても同時にmkBVできない() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                Solver solver1 = ctx1.mkSolver();
//                BitVecExpr one = ctx1.mkBV("1", 256);
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void test_別スレッドかつ別Contextで同時にsolverにaddできない() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                Solver solver1 = ctx1.mkSolver();
//                BoolExpr constraint;
//                synchronized (this) {
//                    BitVecExpr a = ctx1.mkBVConst("a", 256);
//                    BitVecExpr one = ctx1.mkBV("1", 256);
//                    BitVecExpr a_plus_one = ctx1.mkBVAdd(a, one);
//                    constraint = ctx1.mkEq(a_plus_one, ctx1.mkBV(0, 256));
//                }
//                solver1.add(constraint);
//                System.out.println(solver1.check());
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void test_別スレッドかつ別Contextで同時にcheckできない() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                Solver solver1 = ctx1.mkSolver();
//                BoolExpr constraint;
//                synchronized (this) {
//                    BitVecExpr a = ctx1.mkBVConst("a", 256);
//                    BitVecExpr one = ctx1.mkBV("1", 256);
//                    BitVecExpr a_plus_one = ctx1.mkBVAdd(a, one);
//                    constraint = ctx1.mkEq(a_plus_one, ctx1.mkBV(0, 256));
//                    solver1.add(constraint);
//                }
//                System.out.println(solver1.check());
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//
//    @Test
//    public void test_別スレッドかつ別Contextで同時にsolverをtranslateできない() throws InterruptedException {
//        Context ctx = new Context();
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                Solver solver1 = ctx1.mkSolver();
//                Solver sovler2 = solver1.translate(ctx);
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void test_別スレッドかつ別Contextで同時にBVNumをtranslateできない() throws InterruptedException {
//        Context ctx = new Context();
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                BitVecExpr expr;
//                synchronized (this) {
//                    expr = ctx1.mkBV(1, 256);
//                }
//                BitVecExpr expr2 = (BitVecExpr) expr.translate(ctx);
//                System.out.println(expr2);
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void test_別スレッドかつ別Contextで同時にBVConstをtranslateできない() throws InterruptedException {
//        Context ctx = new Context();
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                BitVecExpr expr;
//                synchronized (this) {
//                    expr = ctx1.mkBVConst("a", 256);
//                }
//                BitVecExpr expr2 = (BitVecExpr) expr.translate(ctx);
//                System.out.println(expr2);
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void test_別スレッドかつ別Contextで同時にBVConstをtoStringできる() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                final Context ctx1 = new Context();
//                BitVecExpr expr;
//                synchronized (this) {
//                    expr = ctx1.mkBVConst("a", 256);
//                }
//                System.out.println(expr.toString());
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }

    @Test
    public void test_mkBVUDivで0除算すると最大数になる() {
        Context context = new Context();
        BitVecExpr a = context.mkBV("1", 256);
        BitVecNum zero = context.mkBV("0", 256);
        BitVecNum unknown = (BitVecNum) context.mkBVUDiv(a, zero).simplify();
        System.out.println(unknown.getBigInteger().toString(16));
        // EVMは0を返す
    }

    @Test
    public void test_mkBVSDivで0除算すると最大数になる() {
        Context context = new Context();
        BitVecExpr a = context.mkBV("1", 256);
        BitVecNum zero = context.mkBV("0", 256);
        BitVecNum unknown = (BitVecNum) context.mkBVSDiv(a, zero).simplify();
        System.out.println(unknown.getBigInteger().toString(16));
        // EVMは0を返す
    }


    @Test
    public void test_mkBVURemで0除算すると1になる() {
        Context context = new Context();
        BitVecExpr a = context.mkBV("1", 256);
        BitVecNum zero = context.mkBV("0", 256);
        BitVecNum unknown = (BitVecNum) context.mkBVURem(a, zero).simplify();
        System.out.println(unknown.getBigInteger().toString(16));
        // EVMは0を返す
    }

    @Test
    public void test_mkBVSRemで0除算すると1になる() {
        Context context = new Context();
        BitVecExpr a = context.mkBV("1", 256);
        BitVecNum zero = context.mkBV("0", 256);
        BitVecNum unknown = (BitVecNum) context.mkBVSRem(a, zero).simplify();
        System.out.println(unknown.getBigInteger().toString(16));
        // EVMは0を返す
    }

//    @Test
//    public void test_別スレッドかつ別Contextで同時にmodelをevalできない() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                Context context = new Context();
//                Solver solver = context.mkSolver();
//                BitVecExpr a;
//                synchronized (this) {
//                    a = context.mkBVConst("a", 256);
//                    BitVecExpr one = context.mkBV("1", 256);
//                    BitVecExpr add = context.mkBVAdd(a, one);
//                    BoolExpr b = context.mkEq(add, context.mkBV(0, 256));
//                    solver.add(b);
//                    solver.check();
//                }
//                Model model = solver.getModel();
//                model.eval(a, false);
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    @Test
//    public void test_別スレッドかつ別Contextで同時にsolverをpushとpopできない() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
//                System.out.println("Context " + finalI);
//                Context context = new Context();
//                Solver solver = context.mkSolver();
//                solver.push();
//                synchronized (this) {
//                    BitVecExpr a = context.mkBVConst("a", 256);
//                    BitVecExpr one = context.mkBV("1", 256);
//                    BitVecExpr add = context.mkBVAdd(a, one);
//                    BoolExpr b = context.mkEq(add, context.mkBV(0, 256));
//                    solver.add(b);
//                }
//                solver.pop();
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t -> {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//
//    @Test
//    public void test_別スレッドかつ別Contextでsynchronizeすれば一通りできる() throws InterruptedException {
//        List<Thread> threads = Lists.newArrayList();
//        for (int i = 0; i < 1000; i++) {
//            int finalI = i;
//            final Thread thread = new Thread(() -> {
////                for (int j = 0; j < 100; j++) {
//                Context context = new Context();
//                Solver solver = context.mkSolver();
//                BitVecExpr a;
//                BitVecExpr one;
//                BitVecExpr add;
//                BoolExpr b;
//                Model model;
//                synchronized (this) {
//                    a = context.mkBVConst("a", 256);
//                }
//                synchronized (this) {
//                    one = context.mkBV("1", 256);
//                }
//                synchronized (this) {
//                    add = context.mkBVAdd(a, one);
//                }
//                synchronized (this) {
//                    b = context.mkEq(add, context.mkBV(0, 256));
//                }
//                synchronized (this) {
//                    solver.add(b);
//                }
//                synchronized (this) {
//                    solver.check();
//                }
//                synchronized (this) {
//                    model = solver.getModel();
//                }
//                synchronized (this) {
//                    model.eval(a, false);
//                }
//                System.out.println("Context " + finalI);
////                }
//            });
//            thread.start();
//            threads.add(thread);
//        }
//        threads.forEach(t ->
//        {
//            try {
//                t.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//    }

    private static void trivialZ3Program(int i) {
        try (Context ctx = new Context()) {
            Solver solver = ctx.mkSolver();
            solver.add(ctx.mkEq(ctx.mkInt(3), ctx.mkInt(4)));
            assertThat(solver.check(), equalTo(Status.UNSATISFIABLE));
        }
    }

    @Test
    public void test_マルチスレッドでz３を使える() {
        IntStream.rangeClosed(1, Runtime.getRuntime().availableProcessors() * 2)
                .parallel()
                .forEach(Z3Test::trivialZ3Program);
    }

    @Test
    public void test_mkSolverとgetModelは別のものが返る() {
        Context context = new Context();
        Solver solver1 = context.mkSolver();
        Solver solver2 = context.mkSolver();
        assertNotEquals(solver1, solver2);
    }


    @Test(expected = Z3Exception.class)
    public void test_checkしていない場合はgetModelできない() {
        Context context = new Context();
        Solver solver1 = context.mkSolver();
        Model model1 = solver1.getModel();
    }


    @Test
    public void test_pushとpopしている場合でもcheckしていればgetModelできる() {
        Context context = new Context();
        Solver solver1 = context.mkSolver();
        solver1.push();
        solver1.check();
        solver1.pop();
        // 直近のcheck時のmodelが返る？
        Model model1 = solver1.getModel();
    }


    @Test
    public void test_getModelは別のものが返る() {
        Context context = new Context();
        Solver solver1 = context.mkSolver();
        solver1.check();
        Model model1 = solver1.getModel();
        Model model2 = solver1.getModel();
        assertNotEquals(model1, model2);
    }

    @Test
    public void test_直接translateしていないBVConstがあってもsolverをtranslateしておけばcheckの結果は正しくなる() {
        {
            Context ctx = new Context();
            BitVecExpr a = ctx.mkBVConst("a", 256);
            BitVecExpr one = ctx.mkBV(1, 256);
            BitVecExpr add_one = ctx.mkBVAdd(a, one);
            BoolExpr is_one = ctx.mkEq(add_one, ctx.mkBV(1, 256));
            BitVecExpr two = ctx.mkBV(2, 256);
            BitVecExpr add_two = ctx.mkBVAdd(a, two);
            BoolExpr is_two = ctx.mkEq(add_two, ctx.mkBV(2, 256));
            Solver solver = ctx.mkSolver();
            solver.add(is_one, is_two);
            assertEquals(Status.SATISFIABLE, solver.check());
            Context ctx2 = new Context();
            Solver solver2 = solver.translate(ctx2);
            assertEquals(Status.SATISFIABLE, solver2.check());
        }
        {
            Context ctx = new Context();
            BitVecExpr a = ctx.mkBVConst("a", 256);
            BitVecExpr one = ctx.mkBV(1, 256);
            BitVecExpr add_one = ctx.mkBVAdd(a, one);
            BoolExpr is_one = ctx.mkEq(add_one, ctx.mkBV(1, 256));
            BitVecExpr two = ctx.mkBV(2, 256);
            BitVecExpr add_two = ctx.mkBVAdd(a, two);
            BoolExpr is_two = ctx.mkEq(add_two, ctx.mkBV(11111111, 256));
            Solver solver = ctx.mkSolver();
            solver.add(is_one, is_two);
            assertEquals(Status.UNSATISFIABLE, solver.check());
            Context ctx2 = new Context();
            Solver solver2 = solver.translate(ctx2);
            assertEquals(Status.UNSATISFIABLE, solver2.check());
        }
    }


    @Test
    public void test_translateしたが制約に無関係なBVConstはシンボル名を返す() {
        Context ctx = new Context();
        BitVecExpr a = ctx.mkBVConst("a", 256);
        BitVecExpr b = ctx.mkBVConst("b", 256);
        BitVecExpr one = ctx.mkBV(1, 256);
        BitVecExpr add_one = ctx.mkBVAdd(a, one);
        BoolExpr is_one = ctx.mkEq(add_one, ctx.mkBV(1, 256));
        Solver solver = ctx.mkSolver();
        solver.add(is_one, is_one);
        Context ctx2 = new Context();
        Solver solver2 = solver.translate(ctx2);
        BitVecExpr a2 = (BitVecExpr) a.translate(ctx2);
        BitVecExpr b2 = (BitVecExpr) b.translate(ctx2);
        assertEquals(Status.SATISFIABLE, solver2.check());
        Model model = solver2.getModel();
        assertEquals("0", model.eval(a2, false).toString());
        assertEquals("b", model.eval(b2, false).toString());
    }

    @Test
    public void test_BigInteger_toByteArrayは先頭がffだと00を余分につけて返す() {
        BigInteger bi = new BigInteger("255");
        final byte[] ba = bi.toByteArray();
        assertTrue(Arrays.equals(new byte[]{0, -1}, ba));
        assertEquals("00ff", ByteUtil.toHexString(ba));
    }

    @Test
    public void test_getTacticNames() {
        final Context context = new Context();
        for (String name : context.getTacticNames()) {
            System.out.println(name);
        }
    }

    @Test
    public void test_SolverParams() {
        Context context = new Context();
        final Solver solver = context.mkSolver();
        Params params = context.mkParams();
        params.add("", 10);
        solver.setParameters(params);
        try {
            solver.check();
        } catch (Exception e) {
            // print available params
            e.printStackTrace();
        }
    }

    @Test
    public void test_同じシンボルを複数回mkBVConstできる() {
        Context context = new Context();
        BitVecExpr e = context.mkBVConst("hoge", 123);
        BitVecExpr e2 = context.mkBVConst("hoge", 123);
        Solver solver = context.mkSolver();
        solver.add(context.mkEq(e, e2));
        assertEquals(Status.SATISFIABLE, solver.check());
    }
}
