package com.mathspeed.domain.puzzle;

import java.util.*;

public class MathExpressionEvaluator {

    public static int evaluate(String expression, List<Integer> deck) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("empty_expression");
        }
        if (deck == null) {
            throw new IllegalArgumentException("deck_null");
        }

        String expr = expression.trim();
        List<String> tokens = tokenize(expr);
        if (tokens.isEmpty()) throw new IllegalArgumentException("no_tokens");

        // Validate number usage against deck counts (use absolute value for negative literals)
        validateNumbersAgainstDeck(tokens, deck);

        List<String> rpn = toRPN(tokens);
        return evalRPN(rpn);
    }

    // Tokenization: returns list of tokens: numbers (possibly with leading '-') , operators (+ - * /), parentheses
    private static List<String> tokenize(String s) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        String noSpace = s.replaceAll("\\s+", "");
        int n = noSpace.length();
        String prev = null;

        while (i < n) {
            char c = noSpace.charAt(i);

            if (c == '(') {
                tokens.add("(");
                prev = "(";
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(")");
                prev = ")";
                i++;
                continue;
            }

            if (isOperatorChar(c)) {
                // handle unary minus
                if (c == '-' && (prev == null || prev.equals("(") || isOperatorToken(prev))) {
                    // unary minus: if followed by digits, parse a signed number literal
                    if (i + 1 < n && Character.isDigit(noSpace.charAt(i + 1))) {
                        int j = i + 1;
                        while (j < n && Character.isDigit(noSpace.charAt(j))) j++;
                        String num = "-" + noSpace.substring(i + 1, j);
                        tokens.add(num);
                        prev = num;
                        i = j;
                        continue;
                    } else if (i + 1 < n && noSpace.charAt(i + 1) == '(') {
                        // unary minus before parenthesis: transform "-(" into "0", "-", "("
                        tokens.add("0");
                        tokens.add("-");
                        prev = "-";
                        i++; // move to '(' next iteration
                        continue;
                    } else {
                        throw new IllegalArgumentException("invalid_unary_minus_at_pos:" + i);
                    }
                } else {
                    tokens.add(String.valueOf(c));
                    prev = String.valueOf(c);
                    i++;
                    continue;
                }
            }

            if (Character.isDigit(c)) {
                tokens.add(String.valueOf(c));
                prev = String.valueOf(c);
                i++;
                continue;
            }

            throw new IllegalArgumentException("invalid_char:" + c + " at pos " + i);
        }

        return tokens;
    }

    private static boolean isOperatorChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private static boolean isOperatorToken(String tok) {
        return tok.length() == 1 && isOperatorChar(tok.charAt(0));
    }

    // Validate numeric tokens against deck availability (absolute values considered, only check existence)
    private static void validateNumbersAgainstDeck(List<String> tokens, List<Integer> deck) {
        if (deck == null) return;
        Set<Integer> deckSet = new HashSet<>(deck);

        for (String t : tokens) {
            if (isNumberToken(t)) {
                int val;
                try {
                    val = Integer.parseInt(t);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("invalid_number_token:" + t);
                }
                int abs = Math.abs(val);
                if (!deckSet.contains(abs)) {
                    throw new IllegalArgumentException("number_not_in_deck:" + abs);
                }
            }
        }
    }

    private static boolean isNumberToken(String t) {
        return t.matches("-?\\d+");
    }

    // Convert to RPN using shunting-yard
    private static List<String> toRPN(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();

        for (String t : tokens) {
            if (isNumberToken(t)) {
                output.add(t);
            } else if (isOperatorToken(t)) {
                while (!ops.isEmpty() && isOperatorToken(ops.peek())) {
                    String top = ops.peek();
                    if (precedence(top) >= precedence(t)) {
                        output.add(ops.pop());
                    } else break;
                }
                ops.push(t);
            } else if (t.equals("(")) {
                ops.push(t);
            } else if (t.equals(")")) {
                boolean found = false;
                while (!ops.isEmpty()) {
                    String top = ops.pop();
                    if (top.equals("(")) { found = true; break; }
                    output.add(top);
                }
                if (!found) throw new IllegalArgumentException("mismatched_parentheses");
            } else {
                throw new IllegalArgumentException("unknown_token:" + t);
            }
        }

        while (!ops.isEmpty()) {
            String top = ops.pop();
            if (top.equals("(") || top.equals(")")) throw new IllegalArgumentException("mismatched_parentheses");
            output.add(top);
        }

        return output;
    }

    private static int precedence(String op) {
        if (op.equals("+") || op.equals("-")) return 1;
        if (op.equals("*") || op.equals("/")) return 2;
        return 0;
    }

    // Evaluate RPN, integer arithmetic
    private static int evalRPN(List<String> rpn) {
        Deque<Integer> stack = new ArrayDeque<>();
        for (String t : rpn) {
            if (isNumberToken(t)) {
                stack.push(Integer.parseInt(t));
            } else if (isOperatorToken(t)) {
                if (stack.size() < 2) throw new IllegalArgumentException("malformed_expression");
                int b = stack.pop();
                int a = stack.pop();
                int res;
                switch (t) {
                    case "+" -> res = a + b;
                    case "-" -> res = a - b;
                    case "*" -> res = a * b;
                    case "/" -> {
                        if (b == 0) throw new IllegalArgumentException("division_by_zero");
                        if(a % b != 0) throw new IllegalArgumentException("non_integer_division");
                        res = a / b; // integer division
                    }
                    default -> throw new IllegalArgumentException("unknown_operator:" + t);
                }
                stack.push(res);
            } else {
                throw new IllegalArgumentException("unexpected_token_in_rpn:" + t);
            }
        }
        if (stack.size() != 1) throw new IllegalArgumentException("malformed_expression");
        return stack.pop();
    }
}

