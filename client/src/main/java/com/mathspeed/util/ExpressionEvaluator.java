package com.mathspeed.util;

public class ExpressionEvaluator {

    public double evaluate(String expression) throws Exception {
        return new Parser(expression).parse();
    }

    private static class Parser {
        private String expr;
        private int pos = 0;

        Parser(String expr) {
            this.expr = expr.replaceAll("\\s+", "");
        }

        double parse() throws Exception {
            double result = parseExpression();
            if (pos < expr.length()) {
                throw new Exception("Unexpected character at position " + pos);
            }
            return result;
        }

        private double parseExpression() throws Exception {
            double result = parseTerm();
            while (pos < expr.length() && (expr.charAt(pos) == '+' || expr.charAt(pos) == '-')) {
                char op = expr.charAt(pos++);
                double right = parseTerm();
                result = op == '+' ? result + right : result - right;
            }
            return result;
        }

        private double parseTerm() throws Exception {
            double result = parseFactor();
            while (pos < expr.length() && (expr.charAt(pos) == '*' || expr.charAt(pos) == '/')) {
                char op = expr.charAt(pos++);
                double right = parseFactor();
                if (op == '/' && right == 0) {
                    throw new Exception("Division by zero");
                }
                result = op == '*' ? result * right : result / right;
            }
            return result;
        }

        private double parseFactor() throws Exception {
            if (pos < expr.length() && expr.charAt(pos) == '(') {
                pos++;
                double result = parseExpression();
                if (pos >= expr.length() || expr.charAt(pos) != ')') {
                    throw new Exception("Missing closing parenthesis");
                }
                pos++;
                return result;
            }
            return parseNumber();
        }

        private double parseNumber() throws Exception {
            if (pos >= expr.length()) {
                throw new Exception("Unexpected end of expression");
            }

            StringBuilder num = new StringBuilder();
            while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
                num.append(expr.charAt(pos++));
            }

            if (num.length() == 0) {
                throw new Exception("Invalid number at position " + pos);
            }

            return Double.parseDouble(num.toString());
        }
    }
}
