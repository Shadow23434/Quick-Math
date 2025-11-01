package com.mathspeed.puzzle;

import java.util.*;
import java.util.regex.*;

/**
 * Evaluator biểu thức client gửi lên và so sánh chính xác với target (MathPuzzleUtils.Fraction).
 *
 * Client gửi ví dụ:
 * {
 *   "qCode": "q1",
 *   "answerExpress": "34+7"
 * }
 *
 * Sử dụng:
 *   MathPuzzleUtils.Fraction target = new MathPuzzleUtils.Fraction(10, 3);
 *   boolean ok = new MathExpressionEvaluator().isExpressionValid("3 1/3", allowedNumbers, target);
 *
 * Tính năng:
 * - Hỗ trợ số nguyên, literal fraction "a/b", mixed number "3 1/3" (hoặc "3_1/3" nếu client dùng underscore),
 *   toán tử + - * /, ngoặc (), unary minus.
 * - So sánh chính xác bằng MathPuzzleUtils.Fraction.equals (không dùng double).
 * - Kiểm tra số sử dụng: mỗi số nguyên trong biểu thức phải xuất hiện trong allowedNumbers (mỗi phần tử chỉ dùng một lần).
 *   (Nếu biểu thức chứa literal fraction "1/3", cả 1 và 3 được coi là số và sẽ tiêu thụ allowedNumbers nếu có.)
 */
public class MathExpressionEvaluator {

    /**
     * Kiểm tra biểu thức của client có hợp lệ (chỉ dùng các số cho phép) và cho kết quả đúng bằng target.
     *
     * @param expression biểu thức client gửi (ví dụ "34+7", "3+1/3", "3 1/3", "(2+3)*4")
     * @param allowedNumbers danh sách số nguyên được phép sử dụng (mỗi phần tử chỉ dùng 1 lần)
     * @param targetFraction target chính xác để so sánh
     * @return true nếu hợp lệ và bằng target, false otherwise
     */
    public boolean isExpressionValid(String expression, List<Integer> allowedNumbers, MathPuzzleUtils.Fraction targetFraction) {
        try {
            String normalized = normalizeExpression(expression);

            // Kiểm tra sử dụng số
            if (!validateNumbersUsed(normalized, allowedNumbers)) {
                return false;
            }

            // Parse và evaluate -> trả về Fraction chính xác
            MathPuzzleUtils.Fraction result = parseAndEvaluate(normalized);

            // So sánh exact
            return result.equals(targetFraction);
        } catch (Exception e) {
            // tuỳ log policy, có thể log debug
            System.err.println("Error evaluating expression: " + e.getMessage());
            return false;
        }
    }

    /**
     * Trả về kết quả đánh giá biểu thức dưới dạng Fraction (dùng cho hiển thị / debug).
     */
    public MathPuzzleUtils.Fraction evaluateExpression(String expression) {
        String normalized = normalizeExpression(expression);
        return parseAndEvaluate(normalized);
    }

    // ========================
    // Normalization & helpers
    // ========================

    /**
     * Chuẩn hóa biểu thức:
     * - Chuyển các ký tự nhân/chia phổ biến
     * - Biến các dạng hỗn số "W N/D" hoặc "W_N/D" thành "W+(N/D)" để dễ parse
     * - Bỏ khoảng trắng
     */
    private String normalizeExpression(String expression) {
        if (expression == null) return "";
        // Trước hết chuyển ký tự nhân/chia Unicode về * và /
        expression = expression.replace('×', '*').replace('÷', '/');

        // Hỗ trợ hỗn số dạng "3 1/3" hoặc "3_1/3" -> convert to "3+(1/3)"
        // Pattern: whole (space or underscore) num/den
        Pattern mixed = Pattern.compile("(\\d+)[ _]+(\\d+)/(\\d+)");
        Matcher m = mixed.matcher(expression);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String whole = m.group(1);
            String num = m.group(2);
            String den = m.group(3);
            String replacement = whole + "+(" + num + "/" + den + ")";
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        expression = sb.toString();

        // Loại bỏ whitespace sau xử lý hỗn số
        expression = expression.replaceAll("\\s+", "");

        return expression;
    }

    /**
     * Tách ra các số nguyên xuất hiện trong biểu thức để so khớp với allowedNumbers.
     * - Nhận dạng cả các số trong literal fraction "a/b" (a và b sẽ được lấy)
     * - Nhận dạng các số âm (ví dụ "-3")
     */
    private List<Integer> extractIntegers(String expression) {
        List<Integer> nums = new ArrayList<>();
        // Tìm cả mẫu số hoặc số nguyên: -?digits (đơn giản)
        // Regex đảm bảo không bắt các phần trong số thập phân vì chúng không được hỗ trợ
        Matcher m = Pattern.compile("-?\\d+").matcher(expression);
        while (m.find()) {
            String g = m.group();
            try {
                int v = Integer.parseInt(g);
                nums.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        return nums;
    }

    private boolean validateNumbersUsed(String expression, List<Integer> allowedNumbers) {
        if (allowedNumbers == null) return false;
        List<Integer> available = new ArrayList<>(allowedNumbers);
        List<Integer> used = extractIntegers(expression);

        for (Integer u : used) {
            if (!available.remove(u)) {
                return false;
            }
        }
        return true;
    }

    // ========================
    // Parsing & evaluation
    // ========================

    /**
     * Parse expression (supports integer, literal fraction a/b, + - * /, parentheses, unary minus)
     * and evaluate to MathPuzzleUtils.Fraction.
     */
    private MathPuzzleUtils.Fraction parseAndEvaluate(String expression) {
        List<String> tokens = tokenizeExpressionForFractions(expression);
        List<String> postfix = infixToPostfix(tokens);
        return evaluatePostfix(postfix);
    }

    /**
     * Tokenizer:
     * - integer: digits, optionally with leading '-'
     * - fraction literal: digits '/' digits or -digits/digits
     * - operators: + - * /
     * - parentheses: ( )
     *
     * Handles unary minus by attaching it to number tokens or transforming "-(" into "-1*("
     */
    private List<String> tokenizeExpressionForFractions(String expr) {
        List<String> tokens = new ArrayList<>();
        if (expr == null || expr.isEmpty()) return tokens;

        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '+' || c == '*' || c == '/' || c == '(' || c == ')') {
                tokens.add(String.valueOf(c));
                i++;
            } else if (c == '-') {
                // decide unary or binary
                boolean unary = tokens.isEmpty()
                        || "(".equals(tokens.get(tokens.size() - 1))
                        || "+".equals(tokens.get(tokens.size() - 1))
                        || "-".equals(tokens.get(tokens.size() - 1))
                        || "*".equals(tokens.get(tokens.size() - 1))
                        || "/".equals(tokens.get(tokens.size() - 1));
                if (unary) {
                    // if next is digit, parse signed number/fraction
                    int j = i + 1;
                    if (j < expr.length() && Character.isDigit(expr.charAt(j))) {
                        StringBuilder sb = new StringBuilder();
                        sb.append('-');
                        while (j < expr.length() && Character.isDigit(expr.charAt(j))) {
                            sb.append(expr.charAt(j++));
                        }
                        if (j < expr.length() && expr.charAt(j) == '/') {
                            j++;
                            StringBuilder den = new StringBuilder();
                            while (j < expr.length() && Character.isDigit(expr.charAt(j))) {
                                den.append(expr.charAt(j++));
                            }
                            sb.append('/').append(den);
                        }
                        tokens.add(sb.toString());
                        i = j;
                    } else {
                        // unary minus before parenthesis: convert to (-1)*(...)
                        tokens.add("-1");
                        tokens.add("*");
                        i++;
                    }
                } else {
                    tokens.add("-");
                    i++;
                }
            } else if (Character.isDigit(c)) {
                int j = i;
                StringBuilder num = new StringBuilder();
                while (j < expr.length() && Character.isDigit(expr.charAt(j))) {
                    num.append(expr.charAt(j++));
                }
                if (j < expr.length() && expr.charAt(j) == '/') {
                    // fraction literal
                    j++;
                    StringBuilder den = new StringBuilder();
                    while (j < expr.length() && Character.isDigit(expr.charAt(j))) {
                        den.append(expr.charAt(j++));
                    }
                    tokens.add(num.toString() + "/" + den.toString());
                } else {
                    tokens.add(num.toString());
                }
                i = j;
            } else {
                // skip unknown characters tolerant
                i++;
            }
        }

        return tokens;
    }

    /**
     * Convert infix tokens to postfix using Shunting-yard.
     */
    private List<String> infixToPostfix(List<String> tokens) {
        List<String> out = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        Map<String, Integer> prec = new HashMap<>();
        prec.put("+", 1);
        prec.put("-", 1);
        prec.put("*", 2);
        prec.put("/", 2);

        for (String tok : tokens) {
            if (isNumberOrFractionToken(tok)) {
                out.add(tok);
            } else if ("(".equals(tok)) {
                ops.push(tok);
            } else if (")".equals(tok)) {
                while (!ops.isEmpty() && !"(".equals(ops.peek())) {
                    out.add(ops.pop());
                }
                if (ops.isEmpty() || !"(".equals(ops.peek())) throw new IllegalArgumentException("Mismatched parentheses");
                ops.pop();
            } else if (prec.containsKey(tok)) {
                while (!ops.isEmpty() && prec.containsKey(ops.peek()) && prec.get(ops.peek()) >= prec.get(tok)) {
                    out.add(ops.pop());
                }
                ops.push(tok);
            } else {
                throw new IllegalArgumentException("Unknown token: " + tok);
            }
        }

        while (!ops.isEmpty()) {
            String op = ops.pop();
            if ("(".equals(op) || ")".equals(op)) throw new IllegalArgumentException("Mismatched parentheses");
            out.add(op);
        }
        return out;
    }

    private boolean isNumberOrFractionToken(String tok) {
        return tok.matches("-?\\d+") || tok.matches("-?\\d+/\\d+");
    }

    /**
     * Evaluate postfix to MathPuzzleUtils.Fraction.
     */
    private MathPuzzleUtils.Fraction evaluatePostfix(List<String> postfix) {
        Deque<MathPuzzleUtils.Fraction> stack = new ArrayDeque<>();

        for (String tok : postfix) {
            if (isNumberOrFractionToken(tok)) {
                stack.push(parseTokenToFraction(tok));
            } else {
                if (stack.size() < 2) throw new IllegalArgumentException("Invalid expression");
                MathPuzzleUtils.Fraction b = stack.pop();
                MathPuzzleUtils.Fraction a = stack.pop();
                MathPuzzleUtils.Fraction res;
                switch (tok) {
                    case "+":
                        res = a.add(b);
                        break;
                    case "-":
                        res = a.subtract(b);
                        break;
                    case "*":
                        res = a.multiply(b);
                        break;
                    case "/":
                        res = a.divide(b);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported operator: " + tok);
                }
                stack.push(res);
            }
        }

        if (stack.size() != 1) throw new IllegalArgumentException("Invalid expression");
        return stack.pop();
    }

    private MathPuzzleUtils.Fraction parseTokenToFraction(String tok) {
        // formats: "12", "-12", "1/3", "-1/3"
        if (tok.contains("/")) {
            String[] parts = tok.split("/");
            long num = Long.parseLong(parts[0]);
            long den = Long.parseLong(parts[1]);
            return new MathPuzzleUtils.Fraction(num, den);
        } else {
            long num = Long.parseLong(tok);
            return MathPuzzleUtils.Fraction.whole(num);
        }
    }
}